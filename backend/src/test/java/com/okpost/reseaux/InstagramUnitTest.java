package com.okpost.reseaux;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstagramUnitTest {

    @Test
    void uneLegendeCourteResteInchangee() {
        assertThat(PublicationAutoService.legendeInstagram("Bonjour Douala\n\n#Douala #Foot")).isEqualTo("Bonjour Douala\n\n#Douala #Foot");
    }

    @Test
    void laLegendeEstLimiteeA2200Caracteres() {
        String longue = "mot ".repeat(1000);
        String legende = PublicationAutoService.legendeInstagram(longue);
        assertThat(legende.length()).isLessThanOrEqualTo(2200);
        assertThat(legende).endsWith("…");
    }

    @Test
    void seuls30HashtagsSontGardes() {
        StringBuilder sb = new StringBuilder("Texte\n\n");
        for (int i = 1; i <= 35; i++) sb.append("#tag").append(i).append(' ');
        String legende = PublicationAutoService.legendeInstagram(sb.toString());
        assertThat(legende).contains("#tag30").doesNotContain("#tag31");
    }

    @Test
    void uneImagePngEstConvertieEnJpeg() throws Exception {
        BufferedImage png = new BufferedImage(40, 30, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(png, "png", o);
        byte[] jpeg = PublicationAutoService.enJpeg(o.toByteArray());
        assertThat(jpeg[0] & 0xFF).isEqualTo(0xFF);
        assertThat(jpeg[1] & 0xFF).isEqualTo(0xD8);
        assertThat(ImageIO.read(new ByteArrayInputStream(jpeg)).getWidth()).isEqualTo(40);
    }

    @Test
    void uneImageIllisibleEstRefuseeAvecUnMessageClair() {
        assertThatThrownBy(() -> PublicationAutoService.enJpeg(new byte[]{1, 2, 3}))
                .isInstanceOf(MetaApi.MetaException.class).hasMessageContaining("JPEG");
    }

    @Test
    void libelleDuReseau() {
        assertThat(PublicationAutoService.libelle("INSTAGRAM")).isEqualTo("Instagram");
        assertThat(PublicationAutoService.libelle("FACEBOOK")).isEqualTo("Facebook");
    }
}
