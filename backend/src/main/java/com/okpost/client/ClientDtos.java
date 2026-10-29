package com.okpost.client;

import jakarta.validation.constraints.NotBlank;
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
            UUID cmId) {}

    public record AssetVue(UUID id, String type, String url, String nomOrigine, String legende) {}

    public record ClientVue(
            UUID id, String nom, String secteur, String description, String slogan, Integer anneeCreation,
            String ville, String pays, String zone, String adresse, String horaires, String langue, String ton,
            boolean tutoiement, String motsAEviter, String exemples, List<Client.Offre> offres, List<String> cibles,
            List<String> concurrents, List<String> couleurs, String polices, String domaine,
            Map<String, String> reseaux, String whatsapp, UUID cmId, List<AssetVue> assets, Instant updatedAt) {}

    public record ClientResume(UUID id, String nom, String secteur, String ville, String logoUrl,
                               long aValider, long validees, long aModifier) {}

    public static String urlFichier(String chemin) {
        return "/api/v1/fichiers/" + chemin;
    }
}
