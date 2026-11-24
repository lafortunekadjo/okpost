package com.okpost.calendrier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicationTest {

    @Test
    void tiktokNeAccepteQueLesVideos() {
        assertThat(CalendrierService.incompatibilite("TIKTOK", "IMAGE")).contains("TikTok n'accepte que des vidéos");
        assertThat(CalendrierService.incompatibilite("TIKTOK", "CARROUSEL")).isNotNull();
        assertThat(CalendrierService.incompatibilite("TIKTOK", "VIDEO")).isNull();
    }

    @Test
    void instagramRefuseLeTexteSeul() {
        assertThat(CalendrierService.incompatibilite("INSTAGRAM", "TEXTE")).contains("image ou une vidéo");
        for (String format : new String[]{"IMAGE", "CARROUSEL", "VIDEO", "STORY"}) {
            assertThat(CalendrierService.incompatibilite("INSTAGRAM", format)).as(format).isNull();
        }
    }

    @Test
    void facebookEtLinkedinAcceptentTousLesFormats() {
        for (String format : new String[]{"IMAGE", "CARROUSEL", "VIDEO", "TEXTE", "STORY"}) {
            assertThat(CalendrierService.incompatibilite("FACEBOOK", format)).as(format).isNull();
            assertThat(CalendrierService.incompatibilite("LINKEDIN", format)).as(format).isNull();
        }
    }
}
