package com.okpost.calendrier;

import com.okpost.calendrier.CompositionVisuel.Contact;
import com.okpost.calendrier.CompositionVisuel.TypeContact;
import com.okpost.calendrier.MoteurAffiche.Contenu;
import com.okpost.calendrier.MoteurAffiche.Gabarit;
import com.okpost.client.Client;
import com.okpost.client.Prix;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeviseIconesDiscretTest {

    private final MoteurAffiche moteur = new MoteurAffiche(new CompositionVisuel());
    private final CompositionVisuel composition = new CompositionVisuel();

    private static byte[] photoBlanche(int cote) throws Exception {
        BufferedImage img = new BufferedImage(cote, cote, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, cote, cote);
        g.dispose();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(img, "png", o);
        return o.toByteArray();
    }

    private static BufferedImage lire(byte[] octets) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(octets));
    }

    // ------------------------------------------------------------ devise

    @Test
    void laDeviseNEstAjouteeQueSiLePrixNEnPorteAucune() {
        assertThat(Prix.avecDevise("12 000", "FCFA")).isEqualTo("12 000 FCFA");
        assertThat(Prix.avecDevise("12 000 FCFA", "€")).isEqualTo("12 000 FCFA");
        assertThat(Prix.avecDevise("19,90 €", "FCFA")).isEqualTo("19,90 €");
        assertThat(Prix.avecDevise("$25", "FCFA")).isEqualTo("$25");
        assertThat(Prix.avecDevise("à partir de 5000", "FCFA")).isEqualTo("à partir de 5000");
        assertThat(Prix.avecDevise("5000", null)).isEqualTo("5000");
        assertThat(Prix.avecDevise("5000", " ")).isEqualTo("5000");
        assertThat(Prix.avecDevise("  ", "FCFA")).isNull();
        assertThat(Prix.avecDevise(null, "FCFA")).isNull();
    }

    @Test
    void lAfficheProduitPorteLaDeviseDeLaFiche() {
        var offre = new Client.Offre("Body dentelle", null, "12 000");
        var contenu = new Contenu(Gabarit.PLEIN_CADRE, null, null, "texte", "cta");
        assertThat(VisuelService.avecProduit(contenu, offre, "FCFA").prix()).isEqualTo("12 000 FCFA");
        assertThat(VisuelService.avecProduit(contenu, new Client.Offre("Body", null, "12 000 F"), "FCFA").prix()).isEqualTo("12 000 F");
        assertThat(VisuelService.avecProduit(contenu, offre).prix()).isEqualTo("12 000");
        // Un prix saisi à la main sur la fiche produit reçoit aussi la devise
        var saisi = new Contenu(Gabarit.PRODUIT, null, "5000", null, null);
        assertThat(VisuelService.avecProduit(saisi, offre, "FCFA").prix()).isEqualTo("5000 FCFA");
        // Pour une promotion, « chiffre » reste la remise : seul le prix de la fiche reçoit la devise
        var promo = new Contenu(Gabarit.PROMO, null, "-20 %", null, null);
        var avec = VisuelService.avecProduit(promo, offre, "FCFA");
        assertThat(avec.chiffre()).isEqualTo("-20 %");
        assertThat(avec.prix()).isEqualTo("12 000 FCFA");
    }

    // ------------------------------------------------------------ icônes des réseaux dans le pied de page

    @Test
    void lesReseauxDuPiedDePageSontDesIconesEtPlusDesNoms() {
        var contacts = VisuelService.reseaux(new java.util.LinkedHashMap<>(Map.of(
                "FACEBOOK", "https://www.facebook.com/boutiqueaurore", "INSTAGRAM", "https://www.instagram.com/boutiqueaurore/")));
        assertThat(contacts).hasSize(1);
        assertThat(contacts.get(0).valeur()).isEqualTo("@boutiqueaurore");
        assertThat(contacts.get(0).libelle()).isNull();
        assertThat(contacts.get(0).reseaux()).containsExactlyInAnyOrder(TypeContact.FACEBOOK, TypeContact.INSTAGRAM);
    }

    @Test
    void chaqueIconeDeReseauSeDessineDansLeBandeau() throws Exception {
        byte[] fond = photoBlanche(1080);
        var seul = new CompositionVisuel.Habillage(null, "#15803d", List.of(new Contact(TypeContact.SITE, null, "aurore.cm")));
        BufferedImage un = lire(composition.habiller(fond, seul));
        var quatre = new CompositionVisuel.Habillage(null, "#15803d", List.of(
                new Contact(TypeContact.RESEAU, null, "@aurore", List.of(TypeContact.FACEBOOK, TypeContact.INSTAGRAM, TypeContact.TIKTOK, TypeContact.LINKEDIN))));
        BufferedImage plusieurs = lire(composition.habiller(fond, quatre));
        assertThat(plusieurs.getWidth()).isEqualTo(1080);
        // Les pastilles blanches des icônes : quatre fois plus de blanc dans le bandeau que pour une seule icône
        int bandeau = Math.round(1080 / 100f * CompositionVisuel.hauteurBandeau(1));
        assertThat(blancs(plusieurs, 1080 - bandeau)).isGreaterThan((int) (blancs(un, 1080 - bandeau) * 1.5));
        // Aucun réseau ne plante le dessin, même tous ensemble avec les autres coordonnées
        var tout = new CompositionVisuel.Habillage(null, "#15803d", List.of(
                new Contact(TypeContact.WHATSAPP, null, "+237 677 00 00 00"), new Contact(TypeContact.SITE, null, "aurore.cm"),
                new Contact(TypeContact.RESEAU, null, "@aurore", List.of(TypeContact.FACEBOOK, TypeContact.INSTAGRAM)),
                new Contact(TypeContact.RESEAU, null, "@aurore_tt", List.of(TypeContact.TIKTOK)),
                new Contact(TypeContact.RESEAU, null, "@aurore_in", List.of(TypeContact.LINKEDIN))));
        assertThat(lire(composition.habiller(fond, tout)).getHeight()).isEqualTo(1080);
    }

    /** Pixels très clairs (pastilles et texte blanc) dans la zone du bandeau, sur un fond de marque sombre. */
    private static int blancs(BufferedImage img, int yDebut) {
        int n = 0;
        for (int y = yDebut + 6; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                Color c = new Color(img.getRGB(x, y));
                if (c.getRed() > 235 && c.getGreen() > 235 && c.getBlue() > 235) n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------ texte discret

    /** Part de l'image assombrie ou masquée par le texte et ses voiles (photo de départ entièrement blanche). */
    private double partCouverte(Gabarit gabarit, boolean discret) throws Exception {
        var contenu = new Contenu(gabarit, "Un titre assez long pour occuper de la place sur l'image", "850",
                "Une phrase d'appui qui explique le bénéfice concret pour le client en quelques mots", "Écris-nous sur WhatsApp", null, discret);
        BufferedImage img = lire(moteur.composer(photoBlanche(800), "1:1", contenu, null));
        long couvert = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                Color c = new Color(img.getRGB(x, y));
                if ((c.getRed() + c.getGreen() + c.getBlue()) / 3 < 235) couvert++;
            }
        }
        return couvert / (double) (img.getWidth() * img.getHeight());
    }

    @Test
    void leTexteDiscretCacheNettementMoinsDeLaPhoto() throws Exception {
        for (Gabarit g : new Gabarit[]{Gabarit.PLEIN_CADRE, Gabarit.TITRE_HAUT, Gabarit.DIAGONALE, Gabarit.CHIFFRE}) {
            double normal = partCouverte(g, false);
            double discret = partCouverte(g, true);
            assertThat(discret).as("%s : discret %.2f contre normal %.2f", g, discret, normal).isLessThan(normal * 0.9);
        }
    }
}
