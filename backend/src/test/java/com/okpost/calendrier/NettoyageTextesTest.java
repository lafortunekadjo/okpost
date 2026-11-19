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
}
