package com.okpost.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.time.LocalDate;
import java.util.List;

/** Formats échangés avec l'IA. Les schémas JSON envoyés au modèle sont générés depuis ces records. */
public final class Modeles {

    private Modeles() {}

    // ---- Entrées ----

    public record DemandeCalendrier(LocalDate debut, LocalDate fin, List<String> reseaux,
                                    java.util.Map<String, Integer> rythmeParSemaine,
                                    String objectifs, String tempsForts, String langue,
                                    List<String> piliers, List<String> dejaPrevues) {

        /** Nombre de publications attendues par réseau sur la période (au prorata des jours). */
        public java.util.Map<String, Integer> attendus() {
            long jours = java.time.temporal.ChronoUnit.DAYS.between(debut, fin) + 1;
            java.util.Map<String, Integer> m = new java.util.LinkedHashMap<>();
            rythmeParSemaine.forEach((r, n) -> m.put(r.toUpperCase(java.util.Locale.ROOT),
                    (int) Math.max(1, Math.round(n * jours / 7.0))));
            return m;
        }
    }

    /** Idée déjà placée dans la grille, à détailler. */
    public record IdeeADetailler(int index, LocalDate date, String reseau, String format, String pilier,
                                 String idee, String objectif, String consigne) {}

    // ---- Sorties ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GrilleGeneree(
            @JsonPropertyDescription("Résumé en 3 phrases de l'angle éditorial de la période") String strategie,
            @JsonPropertyDescription("3 à 5 piliers de contenu") List<String> piliers,
            List<IdeeGeneree> publications) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IdeeGeneree(
            @JsonPropertyDescription("Date au format AAAA-MM-JJ, dans la période demandée") LocalDate date,
            @JsonPropertyDescription("FACEBOOK, INSTAGRAM, TIKTOK ou LINKEDIN") String reseau,
            @JsonPropertyDescription("IMAGE, CARROUSEL, VIDEO, TEXTE ou STORY") String format,
            String pilier,
            @JsonPropertyDescription("L'idée de la publication en une phrase concrète") String idee,
            @JsonPropertyDescription("NOTORIETE, ENGAGEMENT, VENTE, CONFIANCE ou RECRUTEMENT") String objectif,
            @JsonPropertyDescription("Fête ou journée liée, sinon null") String tempsFort) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Recommandations(
            @JsonPropertyDescription("Constat en 2 phrases : ce qui a marché, ce qui a moins marché") String constat,
            @JsonPropertyDescription("3 à 5 recommandations concrètes pour la période suivante") List<String> recommandations) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TempsFortsSuggeres(List<TempsFortSuggere> suggestions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TempsFortSuggere(
            @JsonPropertyDescription("Nom court du temps fort") String nom,
            @JsonPropertyDescription("Date AAAA-MM-JJ dans la période, ou null si la date n'est pas certaine") LocalDate date,
            @JsonPropertyDescription("Pourquoi c'est pertinent pour ce client et quel angle prendre, en une phrase") String angle) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LotDetails(List<DetailGenere> publications) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DetailGenere(
            @JsonPropertyDescription("Reprendre l'index de l'idée reçue") int index,
            @JsonPropertyDescription("Légende complète prête à publier") String legende,
            List<String> hashtags,
            @JsonPropertyDescription("Appel à l'action ; numéros et prix uniquement s'ils figurent dans la fiche") String cta,
            @JsonPropertyDescription("Brief visuel ; null pour un format VIDEO") BriefVisuel briefVisuel,
            @JsonPropertyDescription("Scénario ; seulement pour un format VIDEO, sinon null") ScenarioVideo scenario) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BriefVisuel(
            @JsonPropertyDescription("IMAGE, CARROUSEL ou STORY") String type,
            @JsonPropertyDescription("Ce que le graphiste doit comprendre, en une phrase") String description,
            @JsonPropertyDescription("Prompt UNIQUEMENT en anglais, 40 à 90 mots, une seule scène concrète ; voir les règles du prompt d'image") String promptImage,
            @JsonPropertyDescription("1:1, 4:5 ou 9:16") String ratio,
            List<TexteCarrousel> textesCarrousel,
            @JsonPropertyDescription("Titre accrocheur posé sur le visuel, 3 à 7 mots, dans la langue des contenus") String accroche,
            @JsonPropertyDescription("Mise en page de l'affiche : CHIFFRE (un chiffre fort), TITRE_HAUT (titre + texte), DIAGONALE (annonce, offre) ou PLEIN_CADRE (photo dominante)") String gabarit,
            @JsonPropertyDescription("Chiffre ou prix mis en avant (« 850 », « 0 F », « -30 % »), UNIQUEMENT s'il figure dans la fiche ou l'idée, sinon null") String chiffre,
            @JsonPropertyDescription("Phrase d'appui sous le titre, 8 à 20 mots, dans la langue des contenus ; null si inutile") String texte,
            @JsonPropertyDescription("Texte court du bouton d'appel à l'action, 2 à 5 mots (ex. « Écris-nous sur WhatsApp »)") String cta) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TexteCarrousel(int visuel, String titre, String texte) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScenarioVideo(String titre, int dureeTotaleS,
                                @JsonPropertyDescription("Ce qui est dit ou montré dans les 2 premières secondes") String accroche,
                                List<PlanVideo> plans, List<String> materiel) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlanVideo(int numero, int dureeS, String image, String texteEcran, String voixOff, String son) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TexteRegenere(String texte) {}
}
