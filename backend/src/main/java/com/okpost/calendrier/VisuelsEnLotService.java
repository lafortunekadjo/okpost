package com.okpost.calendrier;

import com.okpost.commun.ApiException;
import com.okpost.securite.Session;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Génère d'un coup les visuels de toutes les publications d'un calendrier qui n'en ont pas, en arrière-plan : le community manager lance,
 * peut fermer la page, puis vérifie le résultat. Une publication après l'autre (les fournisseurs d'images limitent les appels simultanés).
 * L'état est gardé en mémoire : un redémarrage du serveur l'efface, mais les visuels déjà créés restent.
 */
@Service
public class VisuelsEnLotService {

    private static final Logger log = LoggerFactory.getLogger(VisuelsEnLotService.class);
    private static final Duration CONSERVATION = Duration.ofHours(2);
    private static final Set<String> FORMATS_ILLUSTRES = Set.of("IMAGE", "STORY", "TEXTE");

    /** erreurs : une ligne par publication en échec ; ignorees : une ligne par publication écartée, avec la raison. */
    public record Progression(UUID calendrierId, String etat, int total, int faits, int echecs, String courant,
                              List<String> ignorees, List<String> erreurs, Instant debut, Instant fin) {}

    private static final class Etat {
        final UUID calendrierId;
        final int total;
        final List<String> ignorees;
        final List<String> erreurs = new ArrayList<>();
        final Instant debut = Instant.now();
        int faits;
        String courant;
        Instant fin;

        Etat(UUID calendrierId, int total, List<String> ignorees) {
            this.calendrierId = calendrierId;
            this.total = total;
            this.ignorees = ignorees;
        }

        synchronized boolean enCours() { return fin == null; }

        synchronized void en(String publication) { courant = publication; }

        synchronized void reussi() { faits++; }

        synchronized void echec(String message) { erreurs.add(message); }

        synchronized void terminer() { courant = null; fin = Instant.now(); }

        synchronized Progression instantane() {
            return new Progression(calendrierId, fin == null ? "EN_COURS" : "TERMINE", total, faits, erreurs.size(), courant,
                    List.copyOf(ignorees), List.copyOf(erreurs), debut, fin);
        }
    }

    private final CalendrierService calendriers;
    private final PublicationRepository publications;
    private final VisuelGenereRepository visuels;
    private final VisuelService generateur;
    private final Map<UUID, Etat> etats = new ConcurrentHashMap<>();
    private final ExecutorService executeur = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "visuels-en-lot");
        t.setDaemon(true);
        return t;
    });

    public VisuelsEnLotService(CalendrierService calendriers, PublicationRepository publications, VisuelGenereRepository visuels,
                               VisuelService generateur) {
        this.calendriers = calendriers;
        this.publications = publications;
        this.visuels = visuels;
        this.generateur = generateur;
    }

    @PreDestroy
    void arreter() {
        executeur.shutdownNow();
    }

    public Progression demarrer(Session s, UUID calendrierId, boolean remplacer, boolean discret) {
        calendriers.charger(s, calendrierId);
        Etat actuel = etats.get(calendrierId);
        if (actuel != null && actuel.enCours()) {
            throw new ApiException(HttpStatus.CONFLICT, "Une génération de visuels est déjà en cours pour ce calendrier");
        }
        purger();
        List<Publication> pubs = publications.findByCalendrierIdOrderByDatePrevueAscReseauAsc(calendrierId);
        Set<UUID> avecVisuel = pubs.isEmpty() ? Set.of()
                : visuels.findByPublicationIdInOrderByCreatedAtDesc(pubs.stream().map(Publication::getId).toList()).stream()
                        .filter(v -> v.getTypeMedia() == VisuelGenere.TypeMedia.IMAGE)
                        .map(VisuelGenere::getPublicationId).collect(Collectors.toSet());
        List<Publication> cibles = new ArrayList<>();
        List<String> ignorees = new ArrayList<>();
        for (Publication p : pubs) {
            String raison = raisonIgnoree(p, avecVisuel.contains(p.getId()), remplacer);
            if (raison == null) cibles.add(p);
            else ignorees.add(libelle(p) + " : " + raison);
        }
        Etat etat = new Etat(calendrierId, cibles.size(), ignorees);
        etats.put(calendrierId, etat);
        if (cibles.isEmpty()) {
            etat.terminer();
        } else {
            executeur.execute(() -> executer(s, cibles, discret, etat));
        }
        return etat.instantane();
    }

    public Optional<Progression> etat(Session s, UUID calendrierId) {
        calendriers.charger(s, calendrierId);
        return Optional.ofNullable(etats.get(calendrierId)).map(Etat::instantane);
    }

    private void executer(Session s, List<Publication> cibles, boolean discret, Etat etat) {
        try {
            for (Publication p : cibles) {
                String nom = libelle(p);
                etat.en(nom);
                try {
                    generateur.generer(s, p.getId(), null, null, true,
                            new VisuelService.Saisie(null, null, null, null, null, null, discret ? Boolean.TRUE : null), null, null);
                    etat.reussi();
                } catch (ApiException e) {
                    etat.echec(nom + " : " + e.getMessage());
                    if (e.getStatut() == HttpStatus.PAYMENT_REQUIRED) {
                        etat.echec("Génération arrêtée : " + e.getMessage());
                        break;
                    }
                } catch (RuntimeException e) {
                    log.warn("Visuel en lot : {} en échec ({})", nom, e.toString());
                    etat.echec(nom + " : erreur inattendue, réessaie depuis la publication");
                }
            }
        } finally {
            etat.terminer();
        }
    }

    /** null si la publication doit recevoir un visuel, sinon la raison pour laquelle elle est écartée. */
    static String raisonIgnoree(Publication p, boolean aDejaUnVisuel, boolean remplacer) {
        if (!p.isDetailGenere()) return "texte pas encore rédigé";
        if (p.getStatut() == StatutPublication.PROGRAMMEE || p.getStatut() == StatutPublication.PUBLIEE) return "déjà programmée ou publiée";
        if ("VIDEO".equals(p.getFormat())) return "vidéo : à tourner ou à téléverser";
        if ("CARROUSEL".equals(p.getFormat())) return "carrousel : ses diapositives se génèrent dans la publication";
        if (!FORMATS_ILLUSTRES.contains(p.getFormat())) return "format sans image";
        if (p.getBrief() == null || p.getBrief().promptImage() == null || p.getBrief().promptImage().isBlank()) return "pas de description d'image";
        if (aDejaUnVisuel && !remplacer) return "a déjà un visuel";
        return null;
    }

    private static String libelle(Publication p) {
        return p.getDatePrevue() + " · " + p.getReseau();
    }

    private void purger() {
        Instant limite = Instant.now().minus(CONSERVATION);
        etats.values().removeIf(e -> {
            Progression p = e.instantane();
            return p.fin() != null && p.fin().isBefore(limite);
        });
    }
}
