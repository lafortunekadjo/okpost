package com.okpost.client;

import com.okpost.commun.ApiException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LienCommandeTest {

    private static Client boutique(String whatsapp, String pays) {
        Client c = new Client();
        c.setNom("Boutique Aurore");
        c.setWhatsapp(whatsapp);
        c.setPays(pays);
        return c;
    }

    @Test
    void numeroInternationalDepuisLesFormatsCourants() {
        assertThat(LienCommande.numero("+237 677 00 00 00", null)).isEqualTo("237677000000");
        assertThat(LienCommande.numero("00237677000000", null)).isEqualTo("237677000000");
        assertThat(LienCommande.numero("677 00 00 00", "Cameroun")).isEqualTo("237677000000");
        assertThat(LienCommande.numero("0677000000", "Cameroun")).isEqualTo("237677000000");
        assertThat(LienCommande.numero("237677000000", "Cameroun")).isEqualTo("237677000000");
        assertThat(LienCommande.numero("07 01 02 03 04", "Côte d'Ivoire")).isEqualTo("225701020304");
    }

    @Test
    void numeroInutilisableSansIndicatifNiPaysConnu() {
        assertThat(LienCommande.numero("677000000", null)).isNull();
        assertThat(LienCommande.numero("677000000", "Atlantide")).isNull();
        assertThat(LienCommande.numero("+12", null)).isNull();
        assertThat(LienCommande.numero("", "Cameroun")).isNull();
        assertThat(LienCommande.numero(null, "Cameroun")).isNull();
    }

    @Test
    void lienAvecMessageDeCommandeEncodePourLUrl() {
        Client c = boutique("+237677000000", "Cameroun");
        var produit = new Client.Offre("Soutien-gorge Émeraude", null, "12 000 FCFA", null, null, null, List.of("85", "90"), List.of("noir"), null);
        var lien = LienCommande.construire(c, produit);
        assertThat(lien.url()).startsWith("https://wa.me/237677000000?text=Bonjour%20Boutique%20Aurore");
        assertThat(lien.url()).doesNotContain(" ").doesNotContain("+Aurore");
        assertThat(lien.message()).contains("je souhaite commander : Soutien-gorge Émeraude (12 000 FCFA)").contains("Taille : ").contains("Couleur : ");
    }

    @Test
    void sansProduitLeMessageDemandeDesInformations() {
        var lien = LienCommande.construire(boutique("+237677000000", null), null);
        assertThat(lien.message()).contains("plus d'informations").doesNotContain("commander");
    }

    @Test
    void refuseAvecUnMessageClairSansNumeroOuSansIndicatif() {
        assertThatThrownBy(() -> LienCommande.construire(boutique(null, "Cameroun"), null))
                .isInstanceOf(ApiException.class).hasMessageContaining("Aucun numéro WhatsApp");
        assertThatThrownBy(() -> LienCommande.construire(boutique("677000000", null), null))
                .isInstanceOf(ApiException.class).hasMessageContaining("indicatif");
    }
}
