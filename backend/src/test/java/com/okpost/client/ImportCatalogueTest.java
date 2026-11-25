package com.okpost.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.okpost.client.CatalogueImport.Produit;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImportCatalogueTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void lesPrixSontFormatesCommeEnFrancais() {
        assertThat(CatalogueImport.formaterPrix(new BigDecimal("12000.00"), "XAF")).isEqualTo("12 000 FCFA");
        assertThat(CatalogueImport.formaterPrix(new BigDecimal("1500000"), null)).isEqualTo("1 500 000");
        assertThat(CatalogueImport.formaterPrix(new BigDecimal("19.5"), "EUR")).isEqualTo("19,50 €");
        assertThat(CatalogueImport.formaterPrix(new BigDecimal("250"), "XOF")).isEqualTo("250 FCFA");
        assertThat(CatalogueImport.formaterPrix(null, "XAF")).isNull();
        assertThat(CatalogueImport.nombre("12 000,50 F")).isEqualByComparingTo("12000.50");
        assertThat(CatalogueImport.nombre("1,234.50")).isEqualByComparingTo("1234.50");
        assertThat(CatalogueImport.nombre("sur devis")).isNull();
    }

    @Test
    void uneBoutiqueShopifySeLitAvecTaillesCouleursEtPrixMinimal() throws Exception {
        String corps = """
                {"products":[{"title":"Soutien-gorge Émeraude","body_html":"<p>Dentelle <b>douce</b></p>","product_type":"Soutiens-gorge",
                 "options":[{"name":"Taille","values":["85","90"]},{"name":"Couleur","values":["Noir","Nude"]}],
                 "variants":[{"price":"12000.00","available":true},{"price":"13000.00","available":false}],
                 "images":[{"src":"https://cdn.shopify.com/a.jpg"}]},
                 {"title":"Culotte","body_html":"","variants":[{"price":"5000.00","available":false}],"images":[]}]}""";
        List<Produit> l = ParseursCatalogue.shopify(json, corps);
        assertThat(l).hasSize(2);
        Produit a = l.get(0);
        assertThat(a.nom()).isEqualTo("Soutien-gorge Émeraude");
        assertThat(a.description()).isEqualTo("Dentelle douce");
        assertThat(a.prix()).isEqualTo("12 000");
        assertThat(a.tailles()).containsExactly("85", "90");
        assertThat(a.couleurs()).containsExactly("Noir", "Nude");
        assertThat(a.disponible()).isTrue();
        assertThat(a.imageUrl()).isEqualTo("https://cdn.shopify.com/a.jpg");
        assertThat(l.get(1).disponible()).isFalse();
    }

    @Test
    void uneBoutiqueWooCommerceConvertitLePrixDepuisLesUnitesMineures() throws Exception {
        String corps = """
                [{"name":"Ensemble Rosé","short_description":"<p>Coton doux</p>","description":"long",
                  "prices":{"price":"1800000","currency_code":"XAF","currency_minor_unit":2},
                  "images":[{"src":"https://exemple.cm/rose.jpg"}],"categories":[{"name":"Ensembles"}],
                  "attributes":[{"name":"Taille","terms":[{"name":"S"},{"name":"M"}]},{"name":"Couleur","terms":[{"name":"Rose"}]}],
                  "is_in_stock":true}]""";
        Produit p = ParseursCatalogue.woocommerce(json, corps).get(0);
        assertThat(p.prix()).isEqualTo("18 000 FCFA");
        assertThat(p.description()).isEqualTo("Coton doux");
        assertThat(p.categorie()).isEqualTo("Ensembles");
        assertThat(p.tailles()).containsExactly("S", "M");
        assertThat(p.couleurs()).containsExactly("Rose");
        assertThat(p.disponible()).isTrue();
    }

    @Test
    void lesDonneesStructureesDUnePageProduitSontLues() {
        String html = """
                <html><head><script type="application/ld+json">
                {"@context":"https://schema.org","@graph":[{"@type":"WebSite","name":"Boutique"},
                 {"@type":"Product","name":"Body dentelle","description":"Body noir","image":["/img/body.jpg"],"color":"Noir",
                  "offers":{"@type":"Offer","price":"9000","priceCurrency":"XAF","availability":"https://schema.org/OutOfStock"}}]}
                </script></head><body></body></html>""";
        var l = ParseursCatalogue.jsonLd(json, Jsoup.parse(html, "https://exemple.cm/produit/body"), URI.create("https://exemple.cm/produit/body"));
        assertThat(l).hasSize(1);
        assertThat(l.get(0).nom()).isEqualTo("Body dentelle");
        assertThat(l.get(0).prix()).isEqualTo("9 000 FCFA");
        assertThat(l.get(0).disponible()).isFalse();
        assertThat(l.get(0).imageUrl()).isEqualTo("https://exemple.cm/img/body.jpg");
        assertThat(l.get(0).couleurs()).containsExactly("Noir");
    }

    @Test
    void unBlocJsonLdCasseNeBloquePasLesAutres() {
        String html = """
                <script type="application/ld+json">{ pas du json</script>
                <script type="application/ld+json">{"@type":"Product","name":"Nuisette","offers":[{"price":"15000","priceCurrency":"XAF"}]}</script>""";
        var l = ParseursCatalogue.jsonLd(json, Jsoup.parse(html), URI.create("https://exemple.cm/"));
        assertThat(l).extracting(Produit::nom).containsExactly("Nuisette");
    }

    @Test
    void lesLiensDeBoutiqueSontTriesEtLesPaniersEcartes() {
        String html = """
                <a href="/boutique">Boutique</a><a href="/panier">Panier</a><a href="/mon-compte">Compte</a>
                <a href="/produit/body-noir">Body</a><a href="/produit/culotte">Culotte</a>
                <a href="https://autre-site.com/produit/x">Externe</a><a href="/photo.jpg">Image</a><a href="/a-propos">À propos</a>""";
        var liens = CatalogueSiteService.liensCatalogue(Jsoup.parse(html, "https://exemple.cm/"), URI.create("https://exemple.cm/"), 10);
        assertThat(liens).extracting(URI::getPath).containsExactly("/boutique", "/produit/body-noir", "/produit/culotte");
        assertThat(CatalogueSiteService.liensCatalogue(Jsoup.parse(html, "https://exemple.cm/"), URI.create("https://exemple.cm/"), 2)).hasSize(2);
    }

    @Test
    void leNettoyageSupprimeLesDoublonsEtLesAdressesDangereuses() {
        var l = CatalogueImport.nettoyer(List.of(
                new Produit("Body Dentelle", "d", "9 000 F", null, null, null, null, "javascript:alert(1)"),
                new Produit("body  dentelle", null, null, null, null, null, null, null),
                new Produit(" ", null, null, null, null, null, null, null),
                new Produit("Culotte", null, null, null, List.of("S", "S", "M"), null, null, "//cdn.exemple.cm/c.jpg")));
        assertThat(l).hasSize(2);
        assertThat(l.get(0).imageUrl()).isNull();
        assertThat(l.get(1).imageUrl()).isEqualTo("https://cdn.exemple.cm/c.jpg");
        assertThat(l.get(1).tailles()).containsExactly("S", "M");
    }
}
