package com.okpost.calendrier;

import com.okpost.client.AnalyseurPhoto;
import com.okpost.client.ClientAsset;
import com.okpost.client.ClientAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Choisit dans la banque du client la photo qui correspond le mieux au sujet d'une publication,
 * en variant d'une publication à l'autre (une photo déjà utilisée récemment passe après les autres).
 */
@Service
public class BanqueImages {

    /** Score minimal (avant pénalité) pour considérer qu'une photo correspond : au moins un mot-clé et un indice de plus. */
    static final double SEUIL = 3;
    private static final double PENALITE_PAR_USAGE = 1.5;
    private static final Duration FENETRE = Duration.ofDays(45);

    private final ClientAssetRepository assets;
    private final VisuelGenereRepository visuels;

    public BanqueImages(ClientAssetRepository assets, VisuelGenereRepository visuels) {
        this.assets = assets;
        this.visuels = visuels;
    }

    /** Photos de la banque que la sélection automatique peut utiliser. */
    public List<ClientAsset> photos(UUID clientId) {
        return assets.findByClientIdOrderByCreatedAt(clientId).stream()
                .filter(a -> a.getType() == ClientAsset.Type.PHOTO
                        && (a.getMime().equals("image/jpeg") || a.getMime().equals("image/png"))
                        && !a.getTags().contains(AnalyseurPhoto.INUTILISABLE))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ClientAsset> choisir(UUID clientId, String requete) {
        List<ClientAsset> photos = photos(clientId);
        if (photos.isEmpty()) return Optional.empty();
        Set<String> mots = AnalyseurPhoto.mots(requete);
        if (mots.isEmpty()) return Optional.empty();
        Map<UUID, Long> usages = new HashMap<>();
        for (Object[] ligne : visuels.usagesDepuis(photos.stream().map(ClientAsset::getId).toList(),
                Instant.now().minus(FENETRE))) {
            usages.put((UUID) ligne[0], ((Number) ligne[1]).longValue());
        }
        return photos.stream()
                .filter(a -> correspondance(mots, a) >= SEUIL)
                .max(Comparator.comparingDouble((ClientAsset a) ->
                                correspondance(mots, a) - PENALITE_PAR_USAGE * usages.getOrDefault(a.getId(), 0L))
                        .thenComparing(a -> -usages.getOrDefault(a.getId(), 0L)));
    }

    /** Un mot-clé qui recoupe la requête vaut 2, un mot de la description ou de la légende vaut 1. */
    static double correspondance(Set<String> requete, ClientAsset a) {
        double score = 0;
        for (String tag : a.getTags()) {
            if (AnalyseurPhoto.mots(tag).stream().anyMatch(requete::contains)) score += 2;
        }
        score += AnalyseurPhoto.mots(a.getDescription()).stream().filter(requete::contains).count();
        score += AnalyseurPhoto.mots(a.getLegende()).stream().filter(requete::contains).count();
        return score;
    }
}
