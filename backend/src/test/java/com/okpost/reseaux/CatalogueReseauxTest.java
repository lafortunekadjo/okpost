package com.okpost.reseaux;

import com.okpost.ia.Modeles.ProduitExtrait;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogueReseauxTest {

    @Test
    void lesPublicationsSontNumeroteesPourRetrouverLaPhoto() {
        var elements = List.of(
                new CatalogueReseauxService.Element("Nouveau : Soutien-gorge Émeraude\n12 000 F, tailles 85 à 100", "https://scontent.fbcdn.net/a.jpg"),
                new CatalogueReseauxService.Element("Culotte Rosé 5 000 F", "https://scontent.fbcdn.net/b.jpg"));
        String contenu = CatalogueReseauxService.contenu(elements);
        assertThat(contenu).startsWith("[1] Nouveau : Soutien-gorge Émeraude 12 000 F").contains("[2] Culotte Rosé 5 000 F").doesNotContain("\n[1]\n");

        var p = CatalogueReseauxService.vers(new ProduitExtrait("Culotte Rosé", null, "5 000 F", null, null, null, 2), elements);
        assertThat(p.imageUrl()).isEqualTo("https://scontent.fbcdn.net/b.jpg");
        assertThat(p.prix()).isEqualTo("5 000 F");
    }

    @Test
    void unNumeroDeSourceInvalideNeDonneAucunePhoto() {
        var elements = List.of(new CatalogueReseauxService.Element("Texte assez long pour être gardé", "https://x/a.jpg"));
        for (Integer source : new Integer[]{null, 0, 2, -1}) {
            assertThat(CatalogueReseauxService.vers(new ProduitExtrait("P", null, null, null, null, null, source), elements).imageUrl()).isNull();
        }
    }

    @Test
    void leContenuEstPlafonnePourNePasDepasserLeLimiteDeLIa() {
        var elements = new java.util.ArrayList<CatalogueReseauxService.Element>();
        for (int i = 0; i < 100; i++) elements.add(new CatalogueReseauxService.Element("x".repeat(700), null));
        assertThat(CatalogueReseauxService.contenu(elements).length()).isLessThanOrEqualTo(24_000);
    }
}
