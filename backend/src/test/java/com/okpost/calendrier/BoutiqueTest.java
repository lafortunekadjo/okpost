package com.okpost.calendrier;

import com.okpost.client.Client;
import com.okpost.ia.Modeles.BriefVisuel;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;

class BoutiqueTest {

    private static Client boutique(String type) {
        Client c = new Client();
        c.setNom("Boutique Aurore");
        c.setTypeActivite(type);
        c.setWhatsapp("+237600000000");
        c.setOffres(new ArrayList<>(List.of(
                new Client.Offre("Soutien-gorge Émeraude", "Dentelle, sans armatures", "12 000 FCFA", "Tailles 85 à 100 ; noir, nude", List.of(UUID.randomUUID())),
                new Client.Offre("Ensemble Rosé", "Coton doux", "18 000 FCFA"))));
        return c;
    }

    private static BriefVisuel brief(String produit) {
        return new BriefVisuel("IMAGE", "d", "scene", "1:1", null, null, "PRODUIT", null, null, null, produit, null);
    }

    @Test
    void leProduitSeRetrouveSansTenirCompteDeLaCasseNiDesAccents() {
        Client c = boutique("PRODUITS");
        assertThat(VisuelService.produitMisEnAvant(c, brief("soutien-gorge emeraude"))).isNotNull();
        assertThat(VisuelService.produitMisEnAvant(c, brief("Produit inconnu"))).isNull();
        assertThat(VisuelService.produitMisEnAvant(c, brief(null))).isNull();
        assertThat(VisuelService.produitMisEnAvant(c, null)).isNull();
    }

    @Test
    void lePrixEtLeNomViennentDeLaFicheEtNonDeLIa() {
        Client c = boutique("PRODUITS");
        var contenu = new MoteurAffiche.Contenu(MoteurAffiche.Gabarit.PLEIN_CADRE, null, null, "Confort et douceur", "Commander sur WhatsApp");
        var avec = VisuelService.avecProduit(contenu, c.getOffres().get(0));
        assertThat(avec.gabarit()).isEqualTo(MoteurAffiche.Gabarit.PRODUIT);
        assertThat(avec.prix()).isEqualTo("12 000 FCFA");
        assertThat(avec.titre()).isEqualTo("Soutien-gorge Émeraude");
        assertThat(avec.cta()).isEqualTo("Commander sur WhatsApp");
        // Un titre écrit par le CM est conservé
        var titre = VisuelService.avecProduit(new MoteurAffiche.Contenu(MoteurAffiche.Gabarit.DIAGONALE, "Nouveau !", null, null, null), c.getOffres().get(0));
        assertThat(titre.titre()).isEqualTo("Nouveau !");
        assertThat(titre.gabarit()).isEqualTo(MoteurAffiche.Gabarit.DIAGONALE);
    }

    @Test
    void laFichePromptContientLesReglesBoutiqueSeulementPourLesProduits() {
        String texte = FicheTexte.de(boutique("PRODUITS"));
        assertThat(texte).contains("vente de produits (boutique)", "RÈGLES POUR UNE ENTREPRISE QUI VEND DES PRODUITS",
                "[photo du produit disponible]", "[pas de photo du produit]", "variantes : Tailles 85 à 100");
        assertThat(FicheTexte.de(boutique("SERVICES"))).doesNotContain("RÈGLES POUR UNE ENTREPRISE").contains("prestations de services");
        assertThat(FicheTexte.de(boutique("MIXTE"))).contains("RÈGLES POUR UNE ENTREPRISE");
    }

    @Test
    void lAfficheProduitSeDessineAvecEtSansPrix() throws Exception {
        BufferedImage photo = new BufferedImage(800, 800, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(photo, "png", o);
        var moteur = new MoteurAffiche(new CompositionVisuel());
        for (String prix : new String[]{"12 000 FCFA", null}) {
            byte[] sortie = moteur.composer(o.toByteArray(), "1:1",
                    new MoteurAffiche.Contenu(MoteurAffiche.Gabarit.PRODUIT, "Soutien-gorge Émeraude", prix, "Dentelle douce, sans armatures", "Commander sur WhatsApp"), null);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(sortie));
            assertThat(image.getWidth()).isEqualTo(1080);
            assertThat(image.getHeight()).isEqualTo(1080);
        }
    }
}
