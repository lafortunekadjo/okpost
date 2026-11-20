package com.okpost.client;

import com.okpost.client.ExtracteurSite.ContenuSite;
import com.okpost.client.ExtracteurSite.Page;
import com.okpost.commun.ApiException;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportImagesTest {

    private static byte[] image(int largeur, int hauteur, int type, String format) throws Exception {
        BufferedImage img = new BufferedImage(largeur, hauteur, type);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(200, 80, 20));
        g.fillRect(0, 0, largeur, hauteur);
        g.dispose();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(img, format, o);
        return o.toByteArray();
    }

    private static BufferedImage lire(byte[] octets) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(octets));
    }

    @Test
    void unePhotoDevientUnJpegReduitA2000Pixels() throws Exception {
        var sortie = NormalisationImage.normaliser(image(3000, 1500, BufferedImage.TYPE_INT_RGB, "png"), false);
        assertThat(sortie.extension()).isEqualTo("jpg");
        assertThat(sortie.mime()).isEqualTo("image/jpeg");
        BufferedImage lue = lire(sortie.octets());
        assertThat(lue.getWidth()).isEqualTo(2000);
        assertThat(lue.getHeight()).isEqualTo(1000);
    }

    @Test
    void unLogoTransparentResteUnPng() throws Exception {
        var sortie = NormalisationImage.normaliser(image(300, 120, BufferedImage.TYPE_INT_ARGB, "png"), true);
        assertThat(sortie.extension()).isEqualTo("png");
        assertThat(lire(sortie.octets()).getColorModel().hasAlpha()).isTrue();
    }

    @Test
    void unLogoOpaqueDevientUnJpeg() throws Exception {
        assertThat(NormalisationImage.normaliser(image(300, 120, BufferedImage.TYPE_INT_RGB, "png"), true).extension()).isEqualTo("jpg");
    }

    @Test
    void refuseLesImagesTropPetitesOuIllisibles() throws Exception {
        assertThatThrownBy(() -> NormalisationImage.normaliser(image(120, 90, BufferedImage.TYPE_INT_RGB, "png"), false))
                .isInstanceOf(ApiException.class).hasMessageContaining("trop petite");
        assertThatThrownBy(() -> NormalisationImage.normaliser(image(30, 30, BufferedImage.TYPE_INT_RGB, "png"), true))
                .isInstanceOf(ApiException.class).hasMessageContaining("trop petite");
        assertThatThrownBy(() -> NormalisationImage.normaliser("ceci n'est pas une image".getBytes(), false))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void refuseUneImageDontLeNombreDePixelsSaturerait​LaMemoire() throws Exception {
        // 7 000 x 7 000 = 49 millions de pixels, en 1 bit : léger à fabriquer, énorme une fois décodée en couleur
        byte[] bombe = image(7000, 7000, BufferedImage.TYPE_BYTE_BINARY, "png");
        assertThatThrownBy(() -> NormalisationImage.normaliser(bombe, false))
                .isInstanceOf(ApiException.class).hasMessageContaining("trop grande");
    }

    @Test
    void lesImagesWebPSontLues() {
        assertThat(ImageIO.getImageReadersByMIMEType("image/webp").hasNext()).as("lecteur WebP disponible").isTrue();
    }

    @Test
    void nomDeFichierLisibleDepuisLAdresse() {
        assertThat(ImportImageService.nomDepuis("https://site.com/uploads/2024/Équipe_Finale (1).webp?x=1", "jpg")).isEqualTo("équipe_finale-1.jpg");
        assertThat(ImportImageService.nomDepuis("https://site.com/", "png")).isEqualTo("image-du-site.png");
        assertThat(ImportImageService.nomDepuis("pas une adresse", "jpg")).endsWith(".jpg");
    }

    // ------------------------------------------------------------------ photos candidates

    private static final String BASE = "https://boulangerie-soleil.com/";

    private static ContenuSite site(String html) {
        return ExtracteurSite.extraire(List.of(new Page(BASE, Jsoup.parse(html, BASE))));
    }

    @Test
    void retientLesPhotosDuContenuEtEcarteLeReste() {
        ContenuSite c = site("""
                <html><head><meta property="og:image" content="/img/partage.jpg"></head><body>
                <nav><img src="/menu.jpg" width="800" height="600"></nav>
                <main>
                  <img src="/photos/vitrine.jpg" alt="Notre vitrine" width="1200" height="800">
                  <img srcset="/p/four-480.jpg 480w, /p/four-1600.jpg 1600w, /p/four-800.jpg 800w" src="/p/four-480.jpg">
                  <img data-src="/p/equipe.webp" src="/placeholder.gif">
                  <img src="/ico/panier.png" width="48" height="48">
                  <img src="/avatar-client.jpg" alt="avatar">
                  <img src="/logo-partenaire.png" alt="Logo partenaire">
                  <img src="/schema.svg"><img src="/anim.gif" width="900" height="600">
                </main><footer><img src="/pied.jpg" width="900" height="600"></footer></body></html>""");
        assertThat(c.photos()).containsExactly(
                BASE + "img/partage.jpg", BASE + "photos/vitrine.jpg", BASE + "p/four-1600.jpg", BASE + "p/equipe.webp");
    }

    @Test
    void neRetientPasPlusDeDouzePhotos() {
        StringBuilder html = new StringBuilder("<html><body><main>");
        for (int i = 0; i < 30; i++) html.append("<img src=\"/p/").append(i).append(".jpg\">");
        html.append("</main></body></html>");
        assertThat(site(html.toString()).photos()).hasSize(12);
    }

    @Test
    void lesLogosSvgEtIconesNeSontPasProposes() {
        ContenuSite c = site("""
                <html><head><link rel="icon" href="/favicon.ico"><link rel="apple-touch-icon" href="/touch.png"></head>
                <body><img src="/logo.svg" alt="logo"><img src="/img/logo.png" alt="logo"></body></html>""");
        assertThat(c.logos()).contains(BASE + "touch.png", BASE + "img/logo.png")
                .doesNotContain(BASE + "favicon.ico", BASE + "logo.svg");
    }
}
