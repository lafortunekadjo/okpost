package com.okpost.calendrier;

import com.okpost.calendrier.CompositionVisuel.Contact;
import com.okpost.calendrier.CompositionVisuel.TypeContact;
import com.okpost.calendrier.MoteurAffiche.Contenu;
import com.okpost.calendrier.MoteurAffiche.Gabarit;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MoteurAfficheTest {

    private final MoteurAffiche moteur = new MoteurAffiche(new CompositionVisuel());

    private static byte[] photo() throws Exception {
        BufferedImage img = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(214, 160, 90), 0, 1024, new Color(70, 40, 20)));
        g.fillRect(0, 0, 1024, 1024);
        g.setColor(new Color(40, 90, 200));
        g.fillOval(480, 300, 460, 700);
        g.dispose();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(img, "png", o);
        return o.toByteArray();
    }

    private static CompositionVisuel.Habillage marque() {
        return new CompositionVisuel.Habillage(null, "#1D4ED8", List.of(
                new Contact(TypeContact.SITE, null, "my2-0.cloud"),
                new Contact(TypeContact.WHATSAPP, null, "+237 641 131 954")));
    }

    @Test
    void chaqueGabaritProduitUneImageDeLaBonneTaille() throws Exception {
        Path dossier = Path.of("target", "affiches");
        Files.createDirectories(dossier);
        for (Gabarit g : Gabarit.values()) {
            for (String ratio : List.of("1:1", "4:5", "9:16")) {
                byte[] sortie = moteur.composer(photo(), ratio,
                        new Contenu(g, "Ton tournoi mérite mieux", "850", "Poules, phases finales et classements : organise ta compétition gratuitement 🏆",
                                "Crée ta compétition"), marque());
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(sortie));
                assertThat(img.getWidth()).isEqualTo(1080);
                assertThat(img.getHeight()).isEqualTo(Math.round(1080f * (ratio.equals("1:1") ? 1 : ratio.equals("4:5") ? 5 / 4f : 16 / 9f)));
                if (ratio.equals("1:1")) Files.write(dossier.resolve(g.name().toLowerCase() + ".jpg"), sortie);
            }
        }
    }

    @Test
    void titreSeulSansTexteNiBouton() throws Exception {
        byte[] sortie = moteur.composer(photo(), "1:1", new Contenu(Gabarit.PLEIN_CADRE, "Un seul titre", null, null, null),
                new CompositionVisuel.Habillage(null, null, List.of()));
        assertThat(ImageIO.read(new ByteArrayInputStream(sortie))).isNotNull();
    }

    @Test
    void leGabaritSeChoisitSelonLeContenu() {
        assertThat(MoteurAffiche.resoudre(null, null, "850", "texte")).isEqualTo(Gabarit.CHIFFRE);
        assertThat(MoteurAffiche.resoudre(null, null, null, "texte")).isEqualTo(Gabarit.TITRE_HAUT);
        assertThat(MoteurAffiche.resoudre(null, null, null, null)).isEqualTo(Gabarit.PLEIN_CADRE);
        assertThat(MoteurAffiche.resoudre("CHIFFRE", null, null, "t")).isEqualTo(Gabarit.TITRE_HAUT);
        assertThat(MoteurAffiche.resoudre("AUTO", "DIAGONALE", null, null)).isEqualTo(Gabarit.DIAGONALE);
        assertThat(MoteurAffiche.resoudre("DIAGONALE", "PLEIN_CADRE", null, null)).isEqualTo(Gabarit.DIAGONALE);
    }

    @Test
    void lesEmojisSontRetiresCarLaPoliceNeLesDessinePas() {
        assertThat(MoteurAffiche.nettoyer("Gratuit ⚽ pour 🏆 ton équipe")).isEqualTo("Gratuit pour ton équipe");
        assertThat(MoteurAffiche.nettoyer(null)).isEmpty();
    }
}
