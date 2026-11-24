package com.okpost.reseaux;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProfilPageTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void horairesRegroupesParJoursIdentiques() throws Exception {
        var h = JSON.readTree("{\"mon_1_open\":\"08:00\",\"mon_1_close\":\"18:00\",\"tue_1_open\":\"08:00\",\"tue_1_close\":\"18:00\","
                + "\"wed_1_open\":\"08:00\",\"wed_1_close\":\"18:00\",\"sat_1_open\":\"09:00\",\"sat_1_close\":\"13:00\"}");
        assertThat(MetaApi.horairesTexte(h)).isEqualTo("Lun–Mer 08:00–18:00 ; Sam 09:00–13:00");
    }

    @Test
    void horairesAbsentsOuVides() throws Exception {
        assertThat(MetaApi.horairesTexte(JSON.readTree("{}"))).isNull();
        assertThat(MetaApi.horairesTexte(JSON.missingNode())).isNull();
        assertThat(MetaApi.horairesTexte(null)).isNull();
    }

    @Test
    void domaineDepuisLeSiteDeLaPage() {
        assertThat(FicheDepuisReseauxService.domaine("www.Exemple.com/boutique")).isEqualTo("exemple.com");
        assertThat(FicheDepuisReseauxService.domaine("https://exemple.cm")).isEqualTo("exemple.cm");
        assertThat(FicheDepuisReseauxService.domaine("  ")).isNull();
        assertThat(FicheDepuisReseauxService.domaine(null)).isNull();
    }
}
