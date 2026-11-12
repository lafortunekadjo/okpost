package com.okpost.calendrier;

import com.okpost.calendrier.MoteurAffiche.Gabarit;
import com.okpost.ia.Modeles.BriefVisuel;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AfficheTest {

    @Test
    void laPhotoEstDemandeeSansTexteEtCadreeSelonLeGabarit() {
        String p = VisuelService.promptPhoto("Young players on a dusty pitch in Douala, golden hour, no text, no letters, no logo",
                Gabarit.CHIFFRE);
        assertThat(p).contains("Young players on a dusty pitch in Douala, golden hour")
                .contains("right half").contains("No text, no letters")
                .doesNotContain("no text, no letters, no logo. Composition");
    }

    @Test
    void leTitreDuCmPasseAvantCeluiDeLIa() {
        var b = new BriefVisuel("IMAGE", "d", "scene", "1:1", null, "Titre IA", null, null, null, null);
        assertThat(VisuelService.contenuAffiche(b, null, new VisuelService.Saisie("Titre du CM", null, null, null, null, null)).titre()).isEqualTo("Titre du CM");
        assertThat(VisuelService.contenuAffiche(b, null, null).titre()).isEqualTo("Titre IA");
    }

    @Test
    void uneDiapositiveDeCarrouselEstEnPleinCadre() {
        var d = new com.okpost.ia.Modeles.TexteCarrousel(2, "Étape 2", "Crée ton équipe");
        var c = VisuelService.contenuAffiche(null, d, new VisuelService.Saisie(null, "CHIFFRE", null, null, null, null));
        assertThat(c.gabarit()).isEqualTo(Gabarit.PLEIN_CADRE);
        assertThat(c.titre()).isEqualTo("Étape 2");
        assertThat(c.texte()).isEqualTo("Crée ton équipe");
    }

    @Test
    void pseudosDesReseaux() {
        var c = VisuelService.reseaux(new java.util.LinkedHashMap<>(Map.of("TIKTOK", "https://www.tiktok.com/@my2_zero")));
        assertThat(c).hasSize(1);
        assertThat(c.get(0).libelle()).isEqualTo("TikTok");
        assertThat(c.get(0).valeur()).isEqualTo("@my2_zero");
        assertThat(VisuelService.reseaux(Map.of("FACEBOOK", "https://www.facebook.com/share/1FHGa9c3Et/"))).isEmpty();
    }
}
