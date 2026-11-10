package com.okpost.calendrier;

import com.okpost.ia.Modeles.BriefVisuel;
import com.okpost.ia.Modeles.ScenarioVideo;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CalendrierDtos {

    private CalendrierDtos() {}

    public record NouveauCalendrier(
            @NotNull LocalDate debut,
            @NotNull LocalDate fin,
            @NotEmpty Map<String, Integer> rythme,
            @Size(max = 2000) String objectifs,
            @Size(max = 6000) String tempsForts,
            @Pattern(regexp = "fr|en") String langue,
            Boolean analyserPage) {}

    public record CalendrierResume(UUID id, LocalDate debut, LocalDate fin, List<String> reseaux,
                                   String statutGeneration, long publications) {}

    public record CalendrierVue(UUID id, UUID clientId, String clientNom, LocalDate debut, LocalDate fin,
                                List<String> reseaux, Map<String, Integer> rythme, String objectifs,
                                String tempsForts, String langue, String strategie, List<String> piliers,
                                String statutGeneration, String messageErreur, String fournisseurIa,
                                long total, long detaillees, List<PublicationVue> publications,
                                boolean analyserPage, String analysePage) {}

    public record RetourVue(String decision, String commentaire, String legendeProposee, String legendeOrigine,
                            java.time.Instant date) {}

    public record PublicationVue(UUID id, UUID calendrierId, LocalDate date, String reseau, String format,
                                 String pilier, String idee, String objectif, String tempsFort, String legende,
                                 List<String> hashtags, String cta, BriefVisuel brief, ScenarioVideo scenario,
                                 boolean detailGenere, String statut, int version, List<RetourVue> retours,
                                 List<VisuelVue> visuels, java.time.Instant publicationPrevue,
                                 java.time.Instant publieeLe, String lienPost, String erreurPublication,
                                 String modeCarrousel) {}

    public record VisuelVue(UUID id, String url, String prompt, String ratio, String modele, java.time.Instant date,
                            String type, String source, String nomOrigine, Integer diapositive) {}

    /** habiller : ajoute le logo, l'accroche et les coordonnées de la fiche client (oui par défaut). */
    public record DemandeImage(@Size(max = 4000) String prompt, @Pattern(regexp = "1:1|4:5|9:16|16:9") String ratio,
                               Boolean habiller, @Size(max = 120) String accroche,
                               @Pattern(regexp = "gemini|cloudflare") String moteur,
                               @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(20) Integer diapositive) {}

    /** Montage d'un carrousel en vidéo défilante. */
    public record DemandeMontage(@jakarta.validation.constraints.Min(2) @jakarta.validation.constraints.Max(8) Integer secondes,
                                 @Pattern(regexp = "fondu|glissement|zoom") String transition) {}

    public record ModeCarrousel(@Pattern(regexp = "VIDEO|PHOTOS") String mode) {}

    /** Modification partielle : seuls les champs non nuls sont appliqués. */
    public record PublicationPatch(LocalDate date, String reseau, String format, String pilier, String idee,
                                   String objectif, String legende, List<String> hashtags, String cta,
                                   BriefVisuel brief, ScenarioVideo scenario, StatutPublication statut,
                                   Integer version) {}

    public record Regeneration(@NotNull @Pattern(regexp = "legende|cta|tout") String champ,
                               @Size(max = 1000) String consigne) {}
}
