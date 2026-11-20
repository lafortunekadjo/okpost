package com.okpost.client;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClientDtos {

    private ClientDtos() {}

    public record ClientForm(
            @NotBlank @Size(max = 150) String nom,
            @Size(max = 150) String secteur,
            @Size(max = 4000) String description,
            @Size(max = 255) String slogan,
            Integer anneeCreation,
            @Size(max = 100) String ville,
            @Size(max = 100) String pays,
            @Size(max = 255) String zone,
            @Size(max = 255) String adresse,
            @Size(max = 255) String horaires,
            @Pattern(regexp = "fr|en") String langue,
            @Size(max = 255) String ton,
            Boolean tutoiement,
            @Size(max = 2000) String motsAEviter,
            @Size(max = 4000) String exemples,
            List<Client.Offre> offres,
            List<String> cibles,
            List<String> concurrents,
            List<String> couleurs,
            @Size(max = 255) String polices,
            @Size(max = 255) String domaine,
            Map<String, String> reseaux,
            @Size(max = 50) String whatsapp,
            UUID cmId,
            Boolean autoPublication,
            @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d") String heurePublication,
            Boolean fetesReligieuses) {}

    public record AssetVue(UUID id, String type, String url, String nomOrigine, String legende,
                           String description, java.util.List<String> tags, String analyse) {
        public static AssetVue de(ClientAsset a) {
            return new AssetVue(a.getId(), a.getType().name(), urlFichier(a.getChemin()), a.getNomOrigine(), a.getLegende(),
                    a.getDescription(), a.getTags(), a.getAnalyse().name());
        }
    }

    /** Modification d'une photo de la banque : légende, description et mots-clés (null = inchangé). */
    public record AssetModif(@jakarta.validation.constraints.Size(max = 255) String legende,
                             @jakarta.validation.constraints.Size(max = 600) String description,
                             @jakarta.validation.constraints.Size(max = 30) java.util.List<@jakarta.validation.constraints.Size(max = 40) String> tags) {}

    public record ClientVue(
            UUID id, String nom, String secteur, String description, String slogan, Integer anneeCreation,
            String ville, String pays, String zone, String adresse, String horaires, String langue, String ton,
            boolean tutoiement, String motsAEviter, String exemples, List<Client.Offre> offres, List<String> cibles,
            List<String> concurrents, List<String> couleurs, String polices, String domaine,
            Map<String, String> reseaux, String whatsapp, UUID cmId, List<AssetVue> assets, Instant updatedAt,
            boolean autoPublication, String heurePublication, boolean fetesReligieuses) {}

    public record ClientResume(UUID id, String nom, String secteur, String ville, String logoUrl,
                               long aValider, long validees, long aModifier,
                               LocalDate prochaine, java.time.Instant derniere) {}

    /** Adresse du site web à lire pour pré-remplir la fiche. */
    public record DemandeSite(@jakarta.validation.constraints.NotBlank @Size(max = 255) String url, Boolean seulementImages) {}

    /** Image du site d'un client à importer : un logo ou une photo pour la banque d'images. */
    public record DemandeImportImage(@jakarta.validation.constraints.NotBlank @Size(max = 2000) String url,
                                     @jakarta.validation.constraints.NotNull ClientAsset.Type type) {}

    public static String urlFichier(String chemin) {
        return "/api/v1/fichiers/" + chemin;
    }
}
