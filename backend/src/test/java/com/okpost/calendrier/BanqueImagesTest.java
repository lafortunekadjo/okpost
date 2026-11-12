package com.okpost.calendrier;

import com.okpost.client.AnalyseurPhoto;
import com.okpost.client.ClientAsset;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BanqueImagesTest {

    private static ClientAsset photo(String description, String... tags) {
        ClientAsset a = new ClientAsset();
        a.setType(ClientAsset.Type.PHOTO);
        a.setMime("image/jpeg");
        a.setDescription(description);
        a.setTags(List.of(tags));
        return a;
    }

    @Test
    void lesMotsIgnorentAccentsMajusculesPluriels() {
        assertThat(AnalyseurPhoto.mots("Les ÉQUIPES de football, sur le terrain !"))
                .contains("equipe", "football", "terrain")
                .doesNotContain("les", "sur");
    }

    @Test
    void uneRequeteEnAnglaisTrouveUnePhotoEtiqueteeEnFrancaisEtEnAnglais() {
        var groupe = photo("Une équipe de football souriante sur un terrain en terre",
                "équipe", "team", "football", "terrain", "pitch", "groupe", "group", "sourire", "smile");
        var marche = photo("Un marché animé à Douala", "marché", "market", "fruits", "vendeuse", "vendor");
        var requete = AnalyseurPhoto.mots("Tournoi gratuit entre équipes. A smiling football team posing on a dirt pitch, no text");
        assertThat(BanqueImages.correspondance(requete, groupe)).isGreaterThanOrEqualTo(BanqueImages.SEUIL);
        assertThat(BanqueImages.correspondance(requete, marche)).isLessThan(BanqueImages.SEUIL);
    }

    @Test
    void uneRequeteSansRapportNeTrouveRien() {
        var groupe = photo("Une équipe de football", "football", "team");
        var requete = AnalyseurPhoto.mots("Offre de réduction sur les coiffures pour la fête des mères");
        assertThat(BanqueImages.correspondance(requete, groupe)).isLessThan(BanqueImages.SEUIL);
    }

    @Test
    void lesMotsClesSontNettoyes() {
        assertThat(AnalyseurPhoto.nettoyerTags(java.util.Arrays.asList(" Marché ", "marché", "", null, "A".repeat(60))))
                .hasSize(2).first().isEqualTo("marché");
    }
}
