package com.okpost.validation;

import com.okpost.ia.Modeles.BriefVisuel;
import com.okpost.ia.Modeles.ScenarioVideo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ValidationDtos {

    private ValidationDtos() {}

    public record NouveauLien(@NotEmpty List<UUID> publications, @Email String email) {}

    public record LienVue(UUID id, String url, Instant expireLe, boolean revoque, int publications,
                          String emailDestinataire, Instant creeLe, String type) {}

    public record NouveauPartage(@Email String email) {}

    public record MediaPublic(String url, String type) {}

    /** Calendrier en lecture seule : sans prompts, statuts internes ni retours. */
    public record PartagePublication(UUID id, LocalDate date, String reseau, String format, String pilier, String idee,
                                     String objectif, String legende, List<String> hashtags, String cta,
                                     BriefVisuel brief, ScenarioVideo scenario, List<MediaPublic> medias) {}

    public record PartageCalendrier(String clientNom, String logoUrl, List<String> couleurs, LocalDate debut, LocalDate fin,
                                    String strategie, List<String> piliers, String objectifs, String tempsForts,
                                    List<String> reseaux, Instant expireLe, List<PartagePublication> publications) {}

    public record EnvoiEmail(@NotNull @Email String email) {}

    public record PortailPublication(UUID id, LocalDate date, String reseau, String format, String legende,
                                     List<String> hashtags, String cta, BriefVisuel brief, ScenarioVideo scenario,
                                     String statut, String derniereDecision, String dernierCommentaire,
                                     String visuelUrl, String visuelType) {}

    public record Portail(String clientNom, String logoUrl, String couleur, LocalDate debut, LocalDate fin,
                          Instant expireLe, int total, int validees, int aModifier,
                          List<PortailPublication> publications,
                          boolean codeRequis, String validateur) {}

    public record Decision(@NotNull RetourClient.Decision decision, @Size(max = 2000) String commentaire) {}

    public record NouvelleLegende(@NotNull @Size(min = 1, max = 5000) String legende,
                                  @Size(max = 2000) String commentaire) {}
}
