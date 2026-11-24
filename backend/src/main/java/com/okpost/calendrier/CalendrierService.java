package com.okpost.calendrier;

import com.okpost.abonnement.AbonnementService;
import com.okpost.calendrier.CalendrierDtos.CalendrierResume;
import com.okpost.calendrier.CalendrierDtos.CalendrierVue;
import com.okpost.calendrier.CalendrierDtos.NouveauCalendrier;
import com.okpost.calendrier.CalendrierDtos.PublicationPatch;
import com.okpost.calendrier.CalendrierDtos.PublicationVue;
import com.okpost.calendrier.CalendrierDtos.RetourVue;
import com.okpost.client.Client;
import com.okpost.client.ClientService;
import com.okpost.commun.ApiException;
import com.okpost.securite.Session;
import com.okpost.validation.RetourClient;
import com.okpost.validation.RetourClientRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CalendrierService {

    public static final Set<String> RESEAUX = Set.of("FACEBOOK", "INSTAGRAM", "TIKTOK", "LINKEDIN");

    private final CalendrierRepository calendriers;
    private final PublicationRepository publications;
    private final RetourClientRepository retours;
    private final ClientService clients;
    private final AbonnementService abonnements;
    private final ApplicationEventPublisher evenements;
    private final VisuelGenereRepository visuels;
    private final com.okpost.reseaux.PublicationAutoService publicationAuto;
    private final com.okpost.client.StockageFichiers stockage;

    public CalendrierService(CalendrierRepository calendriers, PublicationRepository publications,
                             RetourClientRepository retours, ClientService clients, AbonnementService abonnements,
                             ApplicationEventPublisher evenements, VisuelGenereRepository visuels,
                             @org.springframework.context.annotation.Lazy com.okpost.reseaux.PublicationAutoService publicationAuto,
                             com.okpost.client.StockageFichiers stockage) {
        this.stockage = stockage;
        this.visuels = visuels;
        this.publicationAuto = publicationAuto;
        this.calendriers = calendriers;
        this.publications = publications;
        this.retours = retours;
        this.clients = clients;
        this.abonnements = abonnements;
        this.evenements = evenements;
    }

    @Transactional
    public Calendrier creer(Session s, UUID clientId, NouveauCalendrier n) {
        abonnements.exigerEcriture(s.workspaceId());
        Client client = clients.charger(s, clientId);
        if (n.fin().isBefore(n.debut())) {
            throw ApiException.requeteInvalide("La date de fin doit suivre la date de début");
        }
        if (ChronoUnit.DAYS.between(n.debut(), n.fin()) > 42) {
            throw ApiException.requeteInvalide("Période trop longue : 6 semaines maximum");
        }
        Map<String, Integer> rythme = new LinkedHashMap<>();
        n.rythme().forEach((reseau, nombre) -> {
            String r = reseau.toUpperCase(Locale.ROOT);
            if (!RESEAUX.contains(r)) {
                throw ApiException.requeteInvalide("Réseau non pris en charge : " + reseau);
            }
            if (nombre == null || nombre < 1 || nombre > 14) {
                throw ApiException.requeteInvalide("Rythme invalide pour " + reseau + " (1 à 14 par semaine)");
            }
            rythme.put(r, nombre);
        });
        int quota = abonnements.formule(s.workspaceId()).calendriersParMois();
        long ceMois = calendriers.countByWorkspaceIdAndCreatedAtAfter(s.workspaceId(),
                LocalDate.now().withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        if (ceMois >= quota) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED,
                    "Tu as utilisé tes " + quota + " calendrier(s) IA du mois. Passe à la formule supérieure pour en générer plus.");
        }
        Calendrier c = new Calendrier();
        c.setWorkspaceId(s.workspaceId());
        c.setClientId(client.getId());
        c.setPeriodeDebut(n.debut());
        c.setAnalyserPage(Boolean.TRUE.equals(n.analyserPage()));
        c.setPeriodeFin(n.fin());
        c.setReseaux(new ArrayList<>(rythme.keySet()));
        c.setRythme(rythme);
        c.setObjectifs(n.objectifs());
        c.setTempsForts(n.tempsForts());
        c.setLangue(n.langue() != null ? n.langue() : client.getLangue());
        c.setStatutGeneration(StatutGeneration.EN_ATTENTE);
        calendriers.save(c);
        evenements.publishEvent(new GenerationService.GenerationDemandee(c.getId(), null));
        return c;
    }

    @Transactional
    public void relancer(Session s, UUID id) {
        Calendrier c = charger(s, id);
        if (c.getStatutGeneration() != StatutGeneration.ERREUR && c.getStatutGeneration() != StatutGeneration.TERMINE) {
            throw ApiException.requeteInvalide("La génération est déjà en cours");
        }
        c.setStatutGeneration(StatutGeneration.EN_ATTENTE);
        c.setMessageErreur(null);
        calendriers.save(c);
        evenements.publishEvent(new GenerationService.GenerationDemandee(c.getId(), null));
    }

    @Transactional(readOnly = true)
    public Calendrier charger(Session s, UUID id) {
        Calendrier c = calendriers.findByIdAndWorkspaceId(id, s.workspaceId())
                .orElseThrow(() -> ApiException.introuvable("Calendrier"));
        clients.charger(s, c.getClientId());
        return c;
    }

    @Transactional
    public void supprimer(Session s, UUID id) {
        calendriers.delete(charger(s, id));
    }

    @Transactional(readOnly = true)
    public List<CalendrierResume> lister(Session s, UUID clientId) {
        clients.charger(s, clientId);
        return calendriers.findByClientIdAndWorkspaceIdOrderByPeriodeDebutDesc(clientId, s.workspaceId()).stream()
                .map(c -> new CalendrierResume(c.getId(), c.getPeriodeDebut(), c.getPeriodeFin(), c.getReseaux(),
                        c.getStatutGeneration().name(), publications.countByCalendrierId(c.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CalendrierVue vue(Session s, UUID id) {
        Calendrier c = charger(s, id);
        Client client = clients.charger(s, c.getClientId());
        List<Publication> pubs = publications.findByCalendrierIdOrderByDatePrevueAscReseauAsc(c.getId());
        Map<UUID, List<RetourVue>> parPub = retoursPar(pubs.stream().map(Publication::getId).toList());
        Map<UUID, List<CalendrierDtos.VisuelVue>> images = visuelsPar(pubs.stream().map(Publication::getId).toList());
        List<PublicationVue> vues = pubs.stream().map(p -> vue(p, parPub.getOrDefault(p.getId(), List.of()),
                images.getOrDefault(p.getId(), List.of()))).toList();
        long detaillees = pubs.stream().filter(Publication::isDetailGenere).count();
        return new CalendrierVue(c.getId(), c.getClientId(), client.getNom(), c.getPeriodeDebut(), c.getPeriodeFin(),
                c.getReseaux(), c.getRythme(), c.getObjectifs(), c.getTempsForts(), c.getLangue(), c.getStrategie(),
                c.getPiliers(), c.getStatutGeneration().name(), c.getMessageErreur(), c.getFournisseurIa(),
                pubs.size(), detaillees, vues, c.isAnalyserPage(), c.getAnalysePage());
    }

    @Transactional(readOnly = true)
    public Publication chargerPublication(Session s, UUID id) {
        Publication p = publications.findByIdAndWorkspaceId(id, s.workspaceId())
                .orElseThrow(() -> ApiException.introuvable("Publication"));
        charger(s, p.getCalendrierId());
        return p;
    }

    @Transactional
    public PublicationVue modifier(Session s, UUID id, PublicationPatch m) {
        abonnements.exigerEcriture(s.workspaceId());
        Publication p = chargerPublication(s, id);
        if (m.version() != null && m.version() != p.getVersion()) {
            throw new ApiException(HttpStatus.CONFLICT, "Cette publication a été modifiée entre-temps. Recharge la page.");
        }
        boolean horaireModifie = false;
        if (m.date() != null) {
            horaireModifie = !m.date().equals(p.getDatePrevue());
            p.setDatePrevue(m.date());
        }
        if (m.heure() != null) {
            String heure = m.heure().isBlank() ? null : NettoyageTextes.heure(m.heure());
            if (heure == null && !m.heure().isBlank()) throw ApiException.requeteInvalide("Heure invalide : utilise le format HH:mm (ex. 18:30)");
            horaireModifie |= !java.util.Objects.equals(heure, p.getHeurePrevue());
            if (!java.util.Objects.equals(heure, p.getHeurePrevue())) p.setHeureMotif(null);
            p.setHeurePrevue(heure);
        }
        if (m.reseau() != null) {
            String r = m.reseau().toUpperCase(Locale.ROOT);
            if (!RESEAUX.contains(r)) throw ApiException.requeteInvalide("Réseau invalide");
            p.setReseau(r);
        }
        if (m.format() != null) p.setFormat(m.format().toUpperCase(Locale.ROOT));
        if (m.pilier() != null) p.setPilier(m.pilier());
        if (m.idee() != null) p.setIdee(m.idee());
        if (m.objectif() != null) p.setObjectif(m.objectif());
        if (m.legende() != null) p.setLegende(m.legende());
        if (m.hashtags() != null) p.setHashtags(new ArrayList<>(m.hashtags()));
        if (m.cta() != null) p.setCta(m.cta());
        if (m.brief() != null) p.setBrief(m.brief());
        if (m.scenario() != null) p.setScenario(m.scenario());
        if (m.statut() != null) {
            StatutPublication avant = p.getStatut();
            boolean aProgrammer = m.statut() == StatutPublication.PROGRAMMEE
                    && (avant != StatutPublication.PROGRAMMEE || p.getPublicationPrevue() == null);
            if (aProgrammer) {
                // « Programmée » ne doit jamais être posé sans heure de publication : la tâche planifiée ne publie que
                // les publications dont l'heure est renseignée. On passe donc par la même programmation que le bouton « Programmer ».
                if (avant != StatutPublication.PROGRAMMEE && avant != StatutPublication.VALIDEE) {
                    throw ApiException.requeteInvalide("Seule une publication validée peut être programmée : passe-la d'abord en « Validée ».");
                }
                Client client = clients.charger(s, charger(s, p.getCalendrierId()).getClientId());
                publicationAuto.programmer(p, client, null);
            } else {
                p.setStatut(m.statut());
                if (m.statut() != StatutPublication.PROGRAMMEE) p.setPublicationPrevue(null);
            }
        } else if (p.getStatut() == StatutPublication.A_MODIFIER
                && (m.legende() != null || m.brief() != null || m.scenario() != null || m.cta() != null)) {
            // Une publication corrigée repasse en brouillon, prête à être renvoyée en validation.
            p.setStatut(StatutPublication.BROUILLON);
        }
        // Une publication déjà programmée suit sa nouvelle date ou sa nouvelle heure
        if (horaireModifie && p.getStatut() == StatutPublication.PROGRAMMEE && p.getPublicationPrevue() != null) {
            publicationAuto.programmer(p, clients.charger(s, charger(s, p.getCalendrierId()).getClientId()), null);
        }
        publications.saveAndFlush(p);
        return vue(p, retoursPar(List.of(p.getId())).getOrDefault(p.getId(), List.of()),
                visuelsPar(List.of(p.getId())).getOrDefault(p.getId(), List.of()));
    }

    private static final Map<String, String> NOMS_RESEAUX = Map.of("FACEBOOK", "Facebook", "INSTAGRAM", "Instagram", "TIKTOK", "TikTok", "LINKEDIN", "LinkedIn");

    /**
     * Met la même publication sur d'autres réseaux : le texte, les hashtags, le visuel et l'heure sont copiés dans une nouvelle publication
     * par réseau (chacune a ensuite sa validation, sa programmation et son état). Un réseau incompatible avec le format est ignoré, avec la raison.
     */
    @Transactional
    public CalendrierDtos.ResultatDuplication dupliquer(Session s, UUID publicationId, List<String> reseauxCibles) {
        abonnements.exigerEcriture(s.workspaceId());
        Publication source = chargerPublication(s, publicationId);
        List<Publication> existantes = publications.findByCalendrierIdOrderByDatePrevueAscReseauAsc(source.getCalendrierId());
        List<UUID> creees = new ArrayList<>();
        List<String> ignorees = new ArrayList<>();
        for (String brut : new java.util.LinkedHashSet<>(reseauxCibles)) {
            String cible = brut == null ? "" : brut.trim().toUpperCase(Locale.ROOT);
            if (!RESEAUX.contains(cible)) throw ApiException.requeteInvalide("Réseau invalide : " + brut);
            String nom = NOMS_RESEAUX.getOrDefault(cible, cible);
            if (cible.equals(source.getReseau())) { ignorees.add(nom + " : c'est déjà le réseau de cette publication"); continue; }
            String incompatibilite = incompatibilite(cible, source.getFormat());
            if (incompatibilite != null) { ignorees.add(nom + " : " + incompatibilite); continue; }
            boolean dejaLa = existantes.stream().anyMatch(p -> p.getReseau().equals(cible)
                    && p.getDatePrevue().equals(source.getDatePrevue()) && java.util.Objects.equals(p.getIdee(), source.getIdee()));
            if (dejaLa) { ignorees.add(nom + " : cette publication existe déjà sur ce réseau ce jour-là"); continue; }
            Publication copie = copier(source, cible);
            publications.save(copie);
            copierVisuels(source, copie);
            creees.add(copie.getId());
        }
        return new CalendrierDtos.ResultatDuplication(creees, ignorees, 0);
    }

    /** Même chose pour plusieurs publications d'un calendrier à la fois. */
    @Transactional
    public CalendrierDtos.ResultatDuplication dupliquerSelection(Session s, UUID calendrierId, List<UUID> ids, List<String> reseaux) {
        charger(s, calendrierId);
        List<UUID> creees = new ArrayList<>();
        List<String> ignorees = new ArrayList<>();
        for (UUID id : new java.util.LinkedHashSet<>(ids)) {
            Publication p = chargerPublication(s, id);
            if (!p.getCalendrierId().equals(calendrierId)) throw ApiException.requeteInvalide("Certaines publications n'appartiennent pas à ce calendrier");
            CalendrierDtos.ResultatDuplication r = dupliquer(s, id, reseaux);
            creees.addAll(r.creees());
            String repere = p.getDatePrevue() + " (" + NOMS_RESEAUX.getOrDefault(p.getReseau(), p.getReseau()) + ")";
            r.ignorees().forEach(m -> ignorees.add(repere + " → " + m));
        }
        return new CalendrierDtos.ResultatDuplication(creees, ignorees, 0);
    }

    /** Raison pour laquelle un format ne peut pas aller sur un réseau, sinon null. */
    static String incompatibilite(String cible, String format) {
        if ("TIKTOK".equals(cible) && !"VIDEO".equals(format)) return "TikTok n'accepte que des vidéos (cette publication est au format " + format + ")";
        if ("INSTAGRAM".equals(cible) && "TEXTE".equals(format)) return "Instagram demande une image ou une vidéo (format Texte)";
        return null;
    }

    private static Publication copier(Publication source, String reseau) {
        Publication c = new Publication();
        c.setWorkspaceId(source.getWorkspaceId());
        c.setCalendrierId(source.getCalendrierId());
        c.setDatePrevue(source.getDatePrevue());
        c.setReseau(reseau);
        c.setFormat(source.getFormat());
        c.setPilier(source.getPilier());
        c.setIdee(source.getIdee());
        c.setObjectif(source.getObjectif());
        c.setTempsFort(source.getTempsFort());
        c.setHeurePrevue(source.getHeurePrevue());
        c.setHeureMotif(source.getHeureMotif());
        c.setLegende(source.getLegende());
        c.setHashtags(source.getHashtags() == null ? new ArrayList<>() : new ArrayList<>(source.getHashtags()));
        c.setCta(source.getCta());
        c.setBrief(source.getBrief());
        c.setScenario(source.getScenario());
        c.setModeCarrousel(source.getModeCarrousel());
        c.setDetailGenere(source.isDetailGenere());
        // La copie repart de zéro côté validation et publication : elle doit être validée et programmée à son tour
        c.setStatut(source.isDetailGenere() ? StatutPublication.BROUILLON : StatutPublication.IDEE);
        return c;
    }

    /** Les fichiers sont copiés (et non partagés) : supprimer un visuel sur un réseau ne doit pas l'effacer des autres. */
    private void copierVisuels(Publication source, Publication copie) {
        List<VisuelGenere> anciens = new ArrayList<>(visuels.findByPublicationIdInOrderByCreatedAtDesc(List.of(source.getId())));
        java.util.Collections.reverse(anciens); // du plus ancien au plus récent : l'ordre de création est conservé
        for (VisuelGenere v : anciens) {
            String chemin;
            try (var in = stockage.lire(v.getChemin()).getInputStream()) {
                String ext = v.getChemin().contains(".") ? v.getChemin().substring(v.getChemin().lastIndexOf('.') + 1) : "jpg";
                chemin = stockage.enregistrer(in, ext);
            } catch (java.io.IOException | RuntimeException e) {
                continue; // fichier introuvable : la copie se fait sans ce visuel
            }
            VisuelGenere n = new VisuelGenere();
            n.setWorkspaceId(v.getWorkspaceId());
            n.setPublicationId(copie.getId());
            n.setChemin(chemin);
            n.setMime(v.getMime());
            n.setPrompt(v.getPrompt());
            n.setRatio(v.getRatio());
            n.setFournisseur(v.getFournisseur());
            n.setModele(v.getModele());
            n.setSource(v.getSource());
            n.setTypeMedia(v.getTypeMedia());
            n.setNomOrigine(v.getNomOrigine());
            n.setTaille(v.getTaille());
            n.setDiapositive(v.getDiapositive());
            n.setAssetId(v.getAssetId());
            visuels.saveAndFlush(n);
        }
    }

    @Transactional(readOnly = true)
    public PublicationVue vuePublication(Session s, UUID id) {
        Publication p = chargerPublication(s, id);
        return vue(p, retoursPar(List.of(p.getId())).getOrDefault(p.getId(), List.of()),
                visuelsPar(List.of(p.getId())).getOrDefault(p.getId(), List.of()));
    }

    private Map<UUID, List<CalendrierDtos.VisuelVue>> visuelsPar(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return visuels.findByPublicationIdInOrderByCreatedAtDesc(ids).stream()
                .collect(Collectors.groupingBy(VisuelGenere::getPublicationId, LinkedHashMap::new,
                        Collectors.mapping(VisuelService::vue, Collectors.toList())));
    }

    private Map<UUID, List<RetourVue>> retoursPar(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return retours.findByPublicationIdInOrderByCreatedAtDesc(ids).stream()
                .collect(Collectors.groupingBy(RetourClient::getPublicationId, LinkedHashMap::new,
                        Collectors.mapping(r -> new RetourVue(r.getDecision().name(), r.getCommentaire(),
                                r.getLegendeProposee(), r.getLegendeOrigine(), r.getCreatedAt()), Collectors.toList())));
    }

    public static PublicationVue vue(Publication p, List<RetourVue> retours, List<CalendrierDtos.VisuelVue> images) {
        return new PublicationVue(p.getId(), p.getCalendrierId(), p.getDatePrevue(), p.getReseau(), p.getFormat(),
                p.getPilier(), p.getIdee(), p.getObjectif(), p.getTempsFort(), p.getLegende(), p.getHashtags(),
                p.getCta(), p.getBrief(), p.getScenario(), p.isDetailGenere(), p.getStatut().name(), p.getVersion(),
                retours, images, p.getPublicationPrevue(), p.getPublieeLe(), p.getLienPost(), p.getErreurPublication(),
                p.getModeCarrousel(), p.getHeurePrevue(), p.getHeureMotif());
    }
}
