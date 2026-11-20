package com.okpost.calendrier;

import com.okpost.client.Client;
import com.okpost.client.ClientRepository;
import com.okpost.config.OkpostProperties;
import com.okpost.ia.GenerateurIA;
import com.okpost.ia.Modeles.DemandeCalendrier;
import com.okpost.ia.Modeles.DetailGenere;
import com.okpost.ia.Modeles.GrilleGeneree;
import com.okpost.ia.Modeles.IdeeADetailler;
import com.okpost.ia.Modeles.IdeeGeneree;
import com.okpost.ia.SelecteurIA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Génération en tâche de fond : 1) la grille complète, 2) les détails par lots.
 * L'écran calendrier s'affiche dès l'étape 1 et se complète au fil des lots.
 */
@Service
public class GenerationService {

    public record GenerationDemandee(UUID calendrierId, String consigne) {}

    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

    private final CalendrierRepository calendriers;
    private final PublicationRepository publications;
    private final ClientRepository clients;
    private final SelecteurIA selecteur;
    private final TransactionTemplate tx;
    private final OkpostProperties props;
    private final com.okpost.analyse.AnalysePageService analysePage;

    public GenerationService(CalendrierRepository calendriers, PublicationRepository publications,
                             ClientRepository clients, SelecteurIA selecteur, TransactionTemplate tx,
                             OkpostProperties props, com.okpost.analyse.AnalysePageService analysePage) {
        this.analysePage = analysePage;
        this.calendriers = calendriers;
        this.publications = publications;
        this.clients = clients;
        this.selecteur = selecteur;
        this.tx = tx;
        this.props = props;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void surDemande(GenerationDemandee e) {
        generer(e.calendrierId());
    }

    /** Au redémarrage, les générations interrompues passent en erreur pour pouvoir être relancées. */
    @EventListener(ApplicationReadyEvent.class)
    public void reprendre() {
        tx.executeWithoutResult(s -> calendriers.findAll().stream()
                .filter(c -> c.getStatutGeneration() == StatutGeneration.EN_ATTENTE
                        || c.getStatutGeneration() == StatutGeneration.GRILLE
                        || c.getStatutGeneration() == StatutGeneration.DETAILS)
                .forEach(c -> {
                    c.setStatutGeneration(StatutGeneration.ERREUR);
                    c.setMessageErreur("Génération interrompue par un redémarrage du serveur. Relance-la.");
                }));
    }

    public void generer(UUID calendrierId) {
        try {
            Calendrier cal = tx.execute(s -> calendriers.findById(calendrierId).orElseThrow());
            Client client = tx.execute(s -> clients.findById(cal.getClientId()).orElseThrow());
            String fiche = FicheTexte.de(client) + historiquePage(cal, client);
            GenerateurIA ia = selecteur.pour(cal.getWorkspaceId());

            log.info("Calendrier {} : génération lancée avec {}", calendrierId, ia.nom());
            // Grille semaine par semaine : des demandes courtes que même un petit modèle remplit en entier.
            for (LocalDate debut = cal.getPeriodeDebut(); !debut.isAfter(cal.getPeriodeFin()); debut = debut.plusDays(7)) {
                LocalDate fin = debut.plusDays(6).isAfter(cal.getPeriodeFin()) ? cal.getPeriodeFin() : debut.plusDays(6);
                long existantes = publications.countByCalendrierIdAndDatePrevueBetween(calendrierId, debut, fin);
                long attendues = new DemandeCalendrier(debut, fin, cal.getReseaux(), cal.getRythme(), null, null, null,
                        null, null).attendus().values().stream().mapToInt(Integer::intValue).sum();
                if (existantes * 2 >= attendues) {
                    continue; // semaine déjà générée (relance après une erreur)
                }
                etat(calendrierId, StatutGeneration.GRILLE, null, ia.nom() + " / " + ia.modele());
                Calendrier actuel = tx.execute(s -> calendriers.findById(calendrierId).orElseThrow());
                List<String> deja = tx.execute(s -> publications.findByCalendrierIdOrderByDatePrevueAscReseauAsc(calendrierId)
                        .stream().map(Publication::getIdee).toList());
                List<String> recentes = deja.subList(Math.max(0, deja.size() - 30), deja.size());
                DemandeCalendrier demande = new DemandeCalendrier(debut, fin, cal.getReseaux(), cal.getRythme(),
                        cal.getObjectifs(), cal.getTempsForts(), cal.getLangue(), actuel.getPiliers(), recentes);
                log.info("Calendrier {} : grille du {} au {}", calendrierId, debut, fin);
                GrilleGeneree grille = ia.genererCalendrier(cal.getWorkspaceId(), fiche, demande);
                enregistrerGrille(calendrierId, grille);
            }

            etat(calendrierId, StatutGeneration.DETAILS, null, ia.nom() + " / " + ia.modele());
            detaillerRestantes(cal, fiche, ia, null);
            etat(calendrierId, StatutGeneration.TERMINE, null, ia.nom() + " / " + ia.modele());
            log.info("Calendrier {} : génération terminée", calendrierId);
        } catch (RuntimeException e) {
            log.error("Échec de la génération du calendrier {}", calendrierId, e);
            etat(calendrierId, StatutGeneration.ERREUR, messageLisible(e), null);
        }
    }

    /** Régénère le détail complet d'une seule publication, avec une consigne éventuelle (appel synchrone). */
    public void redetailler(Publication p, String consigne) {
        Calendrier cal = calendriers.findById(p.getCalendrierId()).orElseThrow();
        Client client = clients.findById(cal.getClientId()).orElseThrow();
        GenerateurIA ia = selecteur.pour(cal.getWorkspaceId());
        List<DetailGenere> details = ia.genererDetails(cal.getWorkspaceId(), FicheTexte.de(client), cal.getLangue(),
                List.of(versIdee(0, p, consigne)));
        tx.executeWithoutResult(s -> {
            Publication fraiche = publications.findById(p.getId()).orElseThrow();
            appliquer(fraiche, details.get(0));
            if (fraiche.getStatut() == StatutPublication.A_MODIFIER || fraiche.getStatut() == StatutPublication.IDEE) {
                fraiche.setStatut(StatutPublication.BROUILLON);
            }
            publications.save(fraiche);
        });
    }

    private void detaillerRestantes(Calendrier cal, String fiche, GenerateurIA ia, String consigne) {
        int taille = Math.max(1, props.ia().lotDetails());
        List<Publication> aFaire = tx.execute(s -> publications
                .findByCalendrierIdOrderByDatePrevueAscReseauAsc(cal.getId()).stream()
                .filter(p -> !p.isDetailGenere()).toList());
        for (int i = 0; i < aFaire.size(); i += taille) {
            List<Publication> lot = aFaire.subList(i, Math.min(i + taille, aFaire.size()));
            List<IdeeADetailler> idees = new ArrayList<>();
            for (int k = 0; k < lot.size(); k++) {
                idees.add(versIdee(k, lot.get(k), consigne));
            }
            List<DetailGenere> details = ia.genererDetails(cal.getWorkspaceId(), fiche, cal.getLangue(), idees);
            Map<Integer, DetailGenere> parIndex = details.stream()
                    .collect(Collectors.toMap(DetailGenere::index, Function.identity(), (a, b) -> a));
            tx.executeWithoutResult(s -> {
                for (int k = 0; k < lot.size(); k++) {
                    DetailGenere d = parIndex.get(k);
                    if (d == null) continue;
                    Publication p = publications.findById(lot.get(k).getId()).orElse(null);
                    if (p == null) continue;
                    appliquer(p, d);
                    if (p.getStatut() == StatutPublication.IDEE) {
                        p.setStatut(StatutPublication.BROUILLON);
                    }
                    publications.save(p);
                }
            });
        }
    }

    private void enregistrerGrille(UUID calendrierId, GrilleGeneree grille) {
        tx.executeWithoutResult(s -> {
            Calendrier cal = calendriers.findById(calendrierId).orElseThrow();
            if (cal.getStrategie() == null || cal.getStrategie().isBlank()) {
                cal.setStrategie(grille.strategie());
            }
            if ((cal.getPiliers() == null || cal.getPiliers().isEmpty()) && grille.piliers() != null) {
                cal.setPiliers(new ArrayList<>(grille.piliers()));
            }
            calendriers.save(cal);
            // Heures recommandées par l'analyse de la Page (vides s'il n'y a pas d'historique exploitable)
            com.okpost.analyse.AnalysePageService.HeuresRecommandees reco =
                    com.okpost.analyse.AnalysePageService.heuresDepuisAnalyse(cal.getAnalysePage());
            int rotation = 0;
            for (IdeeGeneree i : grille.publications()) {
                Publication p = new Publication();
                p.setWorkspaceId(cal.getWorkspaceId());
                p.setCalendrierId(cal.getId());
                p.setDatePrevue(i.date());
                p.setReseau(i.reseau().toUpperCase(Locale.ROOT));
                String format = i.format().toUpperCase(Locale.ROOT);
                p.setFormat("TIKTOK".equals(p.getReseau()) ? "VIDEO" : format);
                p.setPilier(i.pilier());
                p.setIdee(NettoyageTextes.idee(i.idee()));
                String heure = NettoyageTextes.heureIa(i.heure());
                String motif = NettoyageTextes.motif(i.heureMotif());
                if (!reco.meilleures().isEmpty()) {
                    if (heure == null) {
                        // Pas d'heure exploitable de l'IA : on prend les meilleures heures de la Page, à tour de rôle
                        heure = String.format(Locale.ROOT, "%02d:00", reco.meilleures().get(rotation++ % reco.meilleures().size()));
                        motif = "Une des meilleures heures de la Page d'après son historique";
                    } else if (reco.aEviter().contains(Integer.parseInt(heure.substring(0, 2)))) {
                        // Heure que l'historique déconseille : on la ramène à la meilleure heure la plus proche
                        int ancienne = Integer.parseInt(heure.substring(0, 2));
                        heure = String.format(Locale.ROOT, "%02d:00", reco.plusProche(ancienne));
                        motif = "Heure ajustée d'après l'historique de la Page (" + ancienne + " h était peu porteuse)";
                    }
                }
                p.setHeurePrevue(heure);
                p.setHeureMotif(heure == null ? null : motif);
                p.setObjectif(i.objectif() == null ? null : i.objectif().toUpperCase(Locale.ROOT));
                p.setTempsFort(i.tempsFort());
                p.setStatut(StatutPublication.IDEE);
                publications.save(p);
            }
        });
    }

    private static IdeeADetailler versIdee(int index, Publication p, String consigne) {
        return new IdeeADetailler(index, p.getDatePrevue(), p.getReseau(), p.getFormat(), p.getPilier(),
                p.getIdee(), p.getObjectif(), consigne);
    }

    static void appliquer(Publication p, DetailGenere d) {
        p.setLegende(d.legende());
        p.setHashtags(d.hashtags() == null ? new ArrayList<>() : new ArrayList<>(d.hashtags()));
        p.setCta(d.cta());
        if ("VIDEO".equals(p.getFormat())) {
            p.setScenario(d.scenario());
            p.setBrief(null);
        } else {
            p.setBrief(d.briefVisuel());
            p.setScenario(null);
        }
        p.setDetailGenere(true);
    }

    /**
     * Analyse de la Page Facebook (90 derniers jours), faite une fois au début de la génération et gardée
     * sur le calendrier : une relance après erreur réutilise la même analyse.
     */
    private String historiquePage(Calendrier cal, Client client) {
        if (!cal.isAnalyserPage()) return "";
        String analyse = cal.getAnalysePage();
        if (analyse == null) {
            analyse = analysePage.resumePourCalendrier(client);
            if (analyse == null) return "";
            String texte = analyse;
            tx.executeWithoutResult(s -> calendriers.findById(cal.getId()).ifPresent(c -> {
                c.setAnalysePage(texte);
                calendriers.save(c);
            }));
            log.info("Calendrier {} : Page Facebook analysée", cal.getId());
        }
        return "\n\nHISTORIQUE DE LA PAGE FACEBOOK\n" + analyse;
    }

    private void etat(UUID calendrierId, StatutGeneration statut, String message, String fournisseur) {
        tx.executeWithoutResult(s -> calendriers.findById(calendrierId).ifPresent(c -> {
            c.setStatutGeneration(statut);
            c.setMessageErreur(message);
            if (fournisseur != null) c.setFournisseurIa(fournisseur);
            calendriers.save(c);
        }));
    }

    static String messageLisible(Throwable e) {
        if (String.valueOf(e.getMessage()).contains("429")) {
            return "Le fournisseur IA limite le nombre de demandes. Relance la génération dans quelques minutes.";
        }
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return m.length() > 500 ? m.substring(0, 500) : m;
    }
}
