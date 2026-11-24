package com.okpost.calendrier;

import com.okpost.calendrier.MoteurAffiche.Article;
import com.okpost.calendrier.MoteurAffiche.Contenu;
import com.okpost.calendrier.MoteurAffiche.Gabarit;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GabaritsBoutiqueTest {

    private final MoteurAffiche moteur = new MoteurAffiche(new CompositionVisuel());

    private static byte[] photo(Color c) throws Exception {
        BufferedImage img = new BufferedImage(600, 800, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        g.fillRect(0, 0, 600, 800);
        g.dispose();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(img, "png", o);
        return o.toByteArray();
    }

    private static BufferedImage lire(byte[] octets) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(octets));
    }

    @Test
    void nouveauteEtPromoSeDessinentSurLaFicheProduit() throws Exception {
        byte[] base = photo(new Color(200, 60, 90));
        for (Gabarit g : new Gabarit[]{Gabarit.NOUVEAUTE, Gabarit.PROMO}) {
            byte[] sortie = moteur.composer(base, "4:5", new Contenu(g, "Soutien-gorge Émeraude", "-20 %", "Dentelle douce", "Commander sur WhatsApp", "12 000 FCFA"), null);
            BufferedImage image = lire(sortie);
            assertThat(image.getWidth()).isEqualTo(1080);
            assertThat(image.getHeight()).isEqualTo(1350);
        }
        // Sans étiquette fournie, NOUVEAUTE écrit « NOUVEAU » sans erreur ; PROMO sans remise ne plante pas non plus
        assertThat(moteur.composer(base, "1:1", new Contenu(Gabarit.NOUVEAUTE, "Titre", null, null, null, "5 000 F"), null)).isNotEmpty();
        assertThat(moteur.composer(base, "1:1", new Contenu(Gabarit.PROMO, "Titre", "", null, null, "5 000 F"), null)).isNotEmpty();
    }

    @Test
    void unePromoSansRemiseRevientAuGabaritSuivant() {
        assertThat(MoteurAffiche.resoudre("PROMO", null, null, null)).isNotEqualTo(Gabarit.PROMO);
        assertThat(MoteurAffiche.resoudre("PROMO", null, "-20 %", null)).isEqualTo(Gabarit.PROMO);
        assertThat(MoteurAffiche.resoudre("COLLECTION", null, null, null)).isEqualTo(Gabarit.COLLECTION);
        assertThat(MoteurAffiche.resoudre("NOUVEAUTE", null, null, null)).isEqualTo(Gabarit.NOUVEAUTE);
    }

    @Test
    void lesDispositionsDeLaGrilleRemplissentLaZoneSansSeChevaucher() {
        for (int n = 2; n <= 4; n++) {
            var cases = MoteurAffiche.disposition(n, 10, 20, 400, 300, 8);
            assertThat(cases).hasSize(n);
            for (float[] a : cases) {
                assertThat(a[0]).isGreaterThanOrEqualTo(10);
                assertThat(a[1]).isGreaterThanOrEqualTo(20);
                assertThat(a[0] + a[2]).isLessThanOrEqualTo(410.01f);
                assertThat(a[1] + a[3]).isLessThanOrEqualTo(320.01f);
            }
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    float[] a = cases.get(i);
                    float[] b = cases.get(j);
                    boolean separes = a[0] + a[2] <= b[0] || b[0] + b[2] <= a[0] || a[1] + a[3] <= b[1] || b[1] + b[3] <= a[1];
                    assertThat(separes).as("cases %d et %d pour n=%d", i, j, n).isTrue();
                }
            }
        }
    }

    @Test
    void laCollectionMontreChaqueProduitDansSaCase() throws Exception {
        List<Article> articles = List.of(
                new Article(photo(new Color(220, 20, 20)), "Soutien-gorge Émeraude", "12 000 FCFA"),
                new Article(photo(new Color(20, 200, 20)), "Culotte Rosé", "5 000 FCFA"),
                new Article(photo(new Color(20, 20, 220)), "Body dentelle", "9 000 FCFA"),
                new Article(photo(new Color(230, 230, 20)), "Nuisette satin", "15 000 FCFA"));
        BufferedImage image = lire(moteur.composerCollection(articles, "1:1", "La sélection du mois", "Commander sur WhatsApp", null));
        assertThat(image.getWidth()).isEqualTo(1080);
        assertThat(image.getHeight()).isEqualTo(1080);
        // Au centre de chaque quart de la grille on retrouve la couleur de la photo correspondante (le bas porte du texte)
        assertThat(dominante(image, 270, 380)).isEqualTo('R');
        assertThat(dominante(image, 810, 380)).isEqualTo('V');
        assertThat(dominante(image, 270, 700)).isEqualTo('B');
        assertThat(dominante(image, 810, 700)).isEqualTo('J');
    }

    @Test
    void laCollectionAccepteDeuxOuTroisProduits() throws Exception {
        for (int n = 2; n <= 3; n++) {
            List<Article> articles = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) articles.add(new Article(photo(new Color(40 * i + 60, 100, 150)), "Produit " + i, "1 000 F"));
            assertThat(lire(moteur.composerCollection(articles, "4:5", null, null, null)).getHeight()).isEqualTo(1350);
        }
    }

    private static char dominante(BufferedImage image, int x, int y) {
        Color c = new Color(image.getRGB(x, y));
        if (c.getRed() > 150 && c.getGreen() < 100) return 'R';
        if (c.getGreen() > 150 && c.getRed() < 100) return 'V';
        if (c.getBlue() > 150 && c.getRed() < 100) return 'B';
        if (c.getRed() > 150 && c.getGreen() > 150) return 'J';
        return '?';
    }
}
