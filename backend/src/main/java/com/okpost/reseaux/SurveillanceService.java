package com.okpost.reseaux;

import com.okpost.calendrier.CalendrierRepository;
import com.okpost.calendrier.Publication;
import com.okpost.calendrier.PublicationRepository;
import com.okpost.calendrier.StatutPublication;
import com.okpost.client.Client;
import com.okpost.client.ClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ce qui demande l'attention du CM sur le tableau de bord : publications qui ne partiront pas, ou qui ont échoué.
 * Chaque alerte dit ce qui se passe et ce qu'il faut faire, au lieu de laisser le CM chercher dans les logs.
 */
@Service
public class SurveillanceService {

    public record Alerte(String type, String niveau, UUID publicationId, UUID clientId, String clientNom,
                         String reseau, LocalDate date, String message) {}

    private static final Duration RETARD_TOLERE = Duration.ofMinutes(15);
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("d/MM à HH:mm");

    private final PublicationRepository publications;
    private final CalendrierRepository calendriers;
    private final ClientRepository clients;
    private final PublicationAutoService auto;

    public SurveillanceService(PublicationRepository publications, CalendrierRepository calendriers,
                               ClientRepository clients, PublicationAutoService auto) {
        this.publications = publications;
        this.calendriers = calendriers;
        this.clients = clients;
        this.auto = auto;
    }

    @Transactional(readOnly = true)
    public List<Alerte> alertes(UUID workspaceId) {
        Instant maintenant = Instant.now();
        Map<UUID, Client> clientParCalendrier = new HashMap<>();
        List<Alerte> alertes = new ArrayList<>();

        for (Publication p : publications.findByWorkspaceIdAndStatut(workspaceId, StatutPublication.PROGRAMMEE)) {
            Client c = clientDe(p, clientParCalendrier);
            if (c == null) continue;
            if (p.getTentatives() > 0) {
                boolean definitif = p.getTentatives() >= PublicationAutoService.TENTATIVES_MAX;
                alertes.add(alerte("ECHEC", definitif ? "ERREUR" : "ATTENTION", p, c,
                        "Publication échouée sur " + PublicationAutoService.libelle(p.getReseau()) + " (" + p.getTentatives() + " essai"
                                + (p.getTentatives() > 1 ? "s" : "") + ")" + (definitif ? ", abandonnée" : ", nouvel essai automatique")
                                + " : " + resume(p.getErreurPublication())));
            } else if (p.getPublicationPrevue() == null) {
                alertes.add(alerte("SANS_HEURE", "ERREUR", p, c,
                        "Programmée sans heure de publication : elle ne partira pas. Ouvre-la et clique sur « Programmer »."));
            } else if (p.getPublicationPrevue().isBefore(maintenant.minus(RETARD_TOLERE))) {
                alertes.add(alerte("EN_RETARD", "ERREUR", p, c,
                        "Prévue le " + HEURE.format(p.getPublicationPrevue().atZone(zone(c))) + " mais pas encore publiée : "
                                + "ouvre-la et clique sur « Publier maintenant »."));
            }
        }

        // Publications validées dont la publication automatique est attendue, mais qui ne sont pas programmées
        LocalDate aujourdhui = LocalDate.now();
        for (Publication p : publications.findByWorkspaceIdAndStatut(workspaceId, StatutPublication.VALIDEE)) {
            if (p.getDatePrevue().isBefore(aujourdhui.minusDays(7)) || p.getDatePrevue().isAfter(aujourdhui.plusDays(3))) continue;
            Client c = clientDe(p, clientParCalendrier);
            if (c == null || !c.isAutoPublication()) continue;
            String raison = auto.raisonNonProgrammable(c, p, true);
            if (raison != null) {
                alertes.add(alerte("NON_PROGRAMMEE", "ATTENTION", p, c, "Validée mais non programmée : " + raison + "."));
            }
        }

        alertes.sort(Comparator.comparing((Alerte a) -> "ERREUR".equals(a.niveau()) ? 0 : 1).thenComparing(Alerte::date));
        return alertes;
    }

    private Client clientDe(Publication p, Map<UUID, Client> cache) {
        return cache.computeIfAbsent(p.getCalendrierId(),
                id -> calendriers.findById(id).flatMap(cal -> clients.findById(cal.getClientId())).orElse(null));
    }

    private static Alerte alerte(String type, String niveau, Publication p, Client c, String message) {
        return new Alerte(type, niveau, p.getId(), c.getId(), c.getNom(), p.getReseau(), p.getDatePrevue(), message);
    }

    static ZoneId zone(Client c) {
        try {
            return ZoneId.of(c.getFuseau());
        } catch (RuntimeException e) {
            return ZoneId.of("Africa/Douala");
        }
    }

    /** Message d'erreur raccourci pour l'affichage. */
    static String resume(String erreur) {
        if (erreur == null || erreur.isBlank()) return "raison inconnue";
        String e = erreur.trim().replaceAll("\\s+", " ");
        return e.length() > 220 ? e.substring(0, 217) + "…" : e;
    }
}
