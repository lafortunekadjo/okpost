package com.okpost.reseaux;

import com.okpost.calendrier.Publication;
import com.okpost.client.Client;
import com.okpost.config.OkpostProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PublicationAutoUnitTest {

    @Test
    void messageAvecLegendeCtaEtHashtags() {
        Publication p = new Publication();
        p.setLegende("Bonjour Douala");
        p.setCta("Écrivez-nous");
        p.setHashtags(List.of("#Douala", "Foot"));
        assertThat(PublicationAutoService.message(p)).isEqualTo("Bonjour Douala\n\nÉcrivez-nous\n\n#Douala #Foot");
    }

    @Test
    void heureParDefautDansLeFuseauDuClient() {
        Publication p = new Publication();
        p.setDatePrevue(LocalDate.of(2026, 11, 5));
        Client c = new Client();
        c.setHeurePublication("18:00");
        c.setFuseau("Africa/Douala");
        assertThat(PublicationAutoService.heureParDefaut(p, c)).isEqualTo(Instant.parse("2026-11-05T17:00:00Z"));
    }

    @Test
    void chiffrementAllerRetour() {
        OkpostProperties props = new OkpostProperties(null, null,
                new OkpostProperties.Jwt("secret-de-test-secret-de-test-secret-de-test", 15, 7),
                null, null, null, null, null, "cle-de-test");
        Chiffreur c = new Chiffreur(props);
        String chiffre = c.chiffrer("EAAB-jeton-de-page");
        assertThat(chiffre).doesNotContain("EAAB");
        assertThat(c.dechiffrer(chiffre)).isEqualTo("EAAB-jeton-de-page");
    }

    @Test
    void lHeureDeLaPublicationPrimeSurCelleDuClient() {
        Publication p = new Publication();
        p.setDatePrevue(LocalDate.of(2026, 11, 5));
        p.setHeurePrevue("12:30");
        Client c = new Client();
        c.setHeurePublication("18:00");
        c.setFuseau("Africa/Douala");
        assertThat(PublicationAutoService.heureParDefaut(p, c)).isEqualTo(Instant.parse("2026-11-05T11:30:00Z"));
    }

    @Test
    void uneHeureInvalideRetombeSurLHeureDuClient() {
        Publication p = new Publication();
        p.setDatePrevue(LocalDate.of(2026, 11, 5));
        p.setHeurePrevue("bientôt");
        Client c = new Client();
        c.setHeurePublication("18:00");
        c.setFuseau("Africa/Douala");
        assertThat(PublicationAutoService.heureParDefaut(p, c)).isEqualTo(Instant.parse("2026-11-05T17:00:00Z"));
    }
}
