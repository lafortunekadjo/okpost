package com.okpost.client;

import com.okpost.commun.ApiException;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogueTest {

    private static CatalogueCsv.Lecture lire(String csv) {
        return CatalogueCsv.lire(csv.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void lireUnCatalogueAvecPointVirgulesEtEntetesAccentues() {
        var l = lire("Nom;Prix;Catégorie;Tailles;Couleurs;Stock;Photo\n"
                + "Soutien-gorge Émeraude;12 000 FCFA;Soutiens-gorge;85, 90, 95;\"noir, nude\";oui;emeraude.jpg\n"
                + "Culotte Rosé;5 000 FCFA;Culottes;S;rose;non;\n");
        assertThat(l.lignes()).hasSize(2);
        var a = l.lignes().get(0);
        assertThat(a.nom()).isEqualTo("Soutien-gorge Émeraude");
        assertThat(a.prix()).isEqualTo("12 000 FCFA");
        assertThat(a.categorie()).isEqualTo("Soutiens-gorge");
        assertThat(a.photos()).containsExactly("emeraude.jpg");
        assertThat(a.disponible()).isTrue();
        assertThat(l.lignes().get(1).disponible()).isFalse();
    }

    @Test
    void virgulesEtGuillemetsEtSautsDeLigneDansUnChamp() {
        var l = lire("produit,price,description,sizes,colors\n"
                + "\"Ensemble Aurore, coton\",\"18,000 F\",\"Doux et\nrespirant, \"\"sans couture\"\"\",\"S,M,L\",\"noir,nude,rouge\"\n");
        var p = l.lignes().get(0);
        assertThat(p.nom()).isEqualTo("Ensemble Aurore, coton");
        assertThat(p.prix()).isEqualTo("18,000 F");
        assertThat(p.description()).contains("sans couture").contains("\n");
        assertThat(p.tailles()).containsExactly("S", "M", "L");
        assertThat(p.couleurs()).containsExactly("noir", "nude", "rouge");
    }

    @Test
    void excelFrancaisEnWindows1252EtBom() {
        byte[] octets = "nom;prix\nChemise de nuit Été;9 500 F\n".getBytes(Charset.forName("windows-1252"));
        assertThat(CatalogueCsv.lire(octets).lignes().get(0).nom()).isEqualTo("Chemise de nuit Été");
        byte[] bom = ("﻿nom;prix\nBody dentelle;7 000 F\n").getBytes(StandardCharsets.UTF_8);
        assertThat(CatalogueCsv.lire(bom).lignes().get(0).nom()).isEqualTo("Body dentelle");
    }

    @Test
    void lesLignesSansNomEtLesColonnesInconnuesSontSignalees() {
        var l = lire("nom;prix;marque\nBody;7 000 F;Aurore\n;3 000 F;Aurore\n");
        assertThat(l.lignes()).hasSize(1);
        assertThat(l.avertissements()).anyMatch(a -> a.contains("marque")).anyMatch(a -> a.contains("sans nom"));
    }

    @Test
    void fichierInvalideOuSansColonneNom() {
        assertThatThrownBy(() -> lire("prix;taille\n5000;S\n")).isInstanceOf(ApiException.class).hasMessageContaining("« nom »");
        assertThatThrownBy(() -> lire("nom;prix\n")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> lire("   ")).isInstanceOf(ApiException.class);
    }

    @Test
    void disponibiliteEtListes() {
        assertThat(CatalogueCsv.disponible("Rupture de stock")).isFalse();
        assertThat(CatalogueCsv.disponible("0")).isFalse();
        assertThat(CatalogueCsv.disponible("épuisé")).isFalse();
        assertThat(CatalogueCsv.disponible("oui")).isTrue();
        assertThat(CatalogueCsv.disponible("12")).isTrue();
        assertThat(CatalogueCsv.disponible("")).isNull();
        assertThat(CatalogueCsv.liste("S; M | L, S")).containsExactly("S", "M", "L");
    }

    @Test
    void lesPhotosSontRattacheesParNomDeFichierAvecOuSansExtension() {
        ClientAsset a = photo("Emeraude.JPG");
        ClientAsset b = photo("rose.png");
        var lecture = lire("nom;photo\nSoutien-gorge;emeraude.jpg\nCulotte;\"rose, inconnue.jpg\"\nBody;emeraude\n");
        var r = CatalogueService.convertir(lecture, List.of(a, b));
        assertThat(r.produits().get(0).photos()).containsExactly(a.getId());
        assertThat(r.produits().get(1).photos()).containsExactly(b.getId());
        assertThat(r.produits().get(2).photos()).containsExactly(a.getId());
        assertThat(r.photosRattachees()).isEqualTo(3);
        assertThat(r.avertissements()).anyMatch(w -> w.contains("inconnue.jpg"));
    }

    private static ClientAsset photo(String nom) {
        ClientAsset a = new ClientAsset();
        a.setId(UUID.randomUUID());
        a.setNomOrigine(nom);
        return a;
    }

    @Test
    void lEnregistrementPlafonneEtNettoieLesProduits() {
        var longues = java.util.Collections.nCopies(30, "x".repeat(50));
        var offres = new java.util.ArrayList<Client.Offre>();
        offres.add(new Client.Offre("  Body  ", "d", "5 000 F", null, null, "  Lingerie ", longues, List.of("noir", " ", "noir"), false));
        offres.add(new Client.Offre(" ", "sans nom", null));
        offres.add(null);
        var propres = ClientService.nettoyerOffres(offres);
        assertThat(propres).hasSize(1);
        var o = propres.get(0);
        assertThat(o.nom()).isEqualTo("Body");
        assertThat(o.categorie()).isEqualTo("Lingerie");
        assertThat(o.tailles()).hasSize(1).allSatisfy(t -> assertThat(t).hasSize(20));
        assertThat(o.couleurs()).containsExactly("noir");
        assertThat(o.disponible()).isFalse();
        assertThat(ClientService.nettoyerOffres(java.util.Collections.nCopies(500, new Client.Offre("a", null, null)))).hasSize(ClientService.OFFRES_MAX);
        assertThat(ClientService.nettoyerOffres(null)).isEmpty();
    }
}
