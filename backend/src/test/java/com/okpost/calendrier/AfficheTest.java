package com.okpost.calendrier;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AfficheTest {

    @Test
    void retireLaConsigneSansTexteEtAjouteAccrocheEtLogo() {
        String p = VisuelService.promptAffiche("Young players on a dusty pitch in Douala, golden hour, no text, no letters, no logo",
                "Ton club mérite mieux", "My2-0", List.of("#3B82F6"), true, 14, "#3B82F6");
        assertThat(p).contains("\"Ton club mérite mieux\"").contains("Top-left corner").contains("Bottom 14%")
                .doesNotContain("no letters, no logo");
    }

    @Test
    void hauteurDuBandeauSelonLeFormat() {
        assertThat(VisuelService.partBandeau("1:1", 2)).isEqualTo(12);
        assertThat(VisuelService.partBandeau("1:1", 4)).isEqualTo(18);
        assertThat(VisuelService.partBandeau("9:16", 2)).isEqualTo(8);
        assertThat(VisuelService.partBandeau("4:5", 0)).isZero();
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
