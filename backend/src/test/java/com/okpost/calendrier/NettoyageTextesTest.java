package com.okpost.calendrier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NettoyageTextesTest {

    @Test
    void retireLaLettreEnTrop() {
        assertThat(NettoyageTextes.idee("R rappeler aux responsables d'équipes de saisir les scores"))
                .isEqualTo("Rappeler aux responsables d'équipes de saisir les scores");
        assertThat(NettoyageTextes.idee("M mettre en avant les 85 groupes actifs")).isEqualTo("Mettre en avant les 85 groupes actifs");
    }

    @Test
    void neToucheAucuneIdeeNormale() {
        assertThat(NettoyageTextes.idee("Rappeler aux responsables d'équipes")).isEqualTo("Rappeler aux responsables d'équipes");
        assertThat(NettoyageTextes.idee("À savoir : les tournois sont gratuits")).isEqualTo("À savoir : les tournois sont gratuits");
        assertThat(NettoyageTextes.idee("A partir de demain, inscris ton équipe")).isEqualTo("A partir de demain, inscris ton équipe");
        assertThat(NettoyageTextes.idee("Mettre en avant")).isEqualTo("Mettre en avant");
        assertThat(NettoyageTextes.idee(null)).isNull();
        assertThat(NettoyageTextes.idee("")).isEmpty();
    }

    @Test
    void normaliseLesHeures() {
        assertThat(NettoyageTextes.heure("9:05")).isEqualTo("09:05");
        assertThat(NettoyageTextes.heure("18h30")).isEqualTo("18:30");
        assertThat(NettoyageTextes.heure(" 07:00 ")).isEqualTo("07:00");
        assertThat(NettoyageTextes.heure("25:00")).isNull();
        assertThat(NettoyageTextes.heure("18:75")).isNull();
        assertThat(NettoyageTextes.heure("soir")).isNull();
        assertThat(NettoyageTextes.heure(null)).isNull();
    }

    @Test
    void l_heureProposeeParLIaDoitEtrePlausible() {
        assertThat(NettoyageTextes.heureIa("12:30")).isEqualTo("12:30");
        assertThat(NettoyageTextes.heureIa("6:00")).isEqualTo("06:00");
        assertThat(NettoyageTextes.heureIa("03:15")).isNull();
        assertThat(NettoyageTextes.heureIa("23:30")).isNull();
        assertThat(NettoyageTextes.heureIa("n'importe quoi")).isNull();
    }
}
