package com.okpost.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.okpost.client.CatalogueImport.Produit;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Lecture des formats de catalogue que les boutiques en ligne publient elles-mêmes : Shopify (products.json), WooCommerce
 * (API Store) et les données structurées schema.org (JSON-LD) de n'importe quelle page produit. Aucune IA, donc fiable et gratuit.
 */
final class ParseursCatalogue {

    private ParseursCatalogue() {}

    private static final Pattern TAILLE = Pattern.compile("(?i)taille|size|pointure|tour de");
    private static final Pattern COULEUR = Pattern.compile("(?i)couleur|color|colour|coloris");

    // ------------------------------------------------------------ Shopify

    static List<Produit> shopify(ObjectMapper json, String corps) throws IOException {
        List<Produit> produits = new ArrayList<>();
        for (JsonNode p : json.readTree(corps).path("products")) {
            String nom = p.path("title").asText("");
            if (nom.isBlank()) continue;
            BigDecimal min = null;
            boolean dispo = false;
            for (JsonNode v : p.path("variants")) {
                BigDecimal prix = CatalogueImport.nombre(v.path("price").asText(""));
                if (prix != null && (min == null || prix.compareTo(min) < 0)) min = prix;
                if (v.path("available").asBoolean(true)) dispo = true;
            }
            List<String> tailles = new ArrayList<>();
            List<String> couleurs = new ArrayList<>();
            for (JsonNode o : p.path("options")) {
                String n = o.path("name").asText("");
                List<String> valeurs = new ArrayList<>();
                o.path("values").forEach(x -> valeurs.add(x.asText("")));
                if (TAILLE.matcher(n).find()) tailles.addAll(valeurs);
                else if (COULEUR.matcher(n).find()) couleurs.addAll(valeurs);
            }
            String image = p.path("images").path(0).path("src").asText("");
            produits.add(new Produit(nom, texteBrut(p.path("body_html").asText("")), CatalogueImport.formaterPrix(min, null),
                    p.path("product_type").asText(""), tailles, couleurs, p.path("variants").isEmpty() ? null : dispo, image));
        }
        return produits;
    }

    // ------------------------------------------------------------ WooCommerce

    static List<Produit> woocommerce(ObjectMapper json, String corps) throws IOException {
        List<Produit> produits = new ArrayList<>();
        for (JsonNode p : json.readTree(corps)) {
            String nom = texteBrut(p.path("name").asText(""));
            if (nom.isBlank()) continue;
            JsonNode prix = p.path("prices");
            BigDecimal montant = CatalogueImport.nombre(prix.path("price").asText(""));
            int decimales = prix.path("currency_minor_unit").asInt(0);
            if (montant != null) montant = montant.movePointLeft(decimales);
            List<String> tailles = new ArrayList<>();
            List<String> couleurs = new ArrayList<>();
            for (JsonNode a : p.path("attributes")) {
                String n = a.path("name").asText("");
                List<String> valeurs = new ArrayList<>();
                a.path("terms").forEach(t -> valeurs.add(t.path("name").asText("")));
                if (TAILLE.matcher(n).find()) tailles.addAll(valeurs);
                else if (COULEUR.matcher(n).find()) couleurs.addAll(valeurs);
            }
            String description = texteBrut(p.path("short_description").asText(""));
            if (description.isBlank()) description = texteBrut(p.path("description").asText(""));
            produits.add(new Produit(nom, description, CatalogueImport.formaterPrix(montant, prix.path("currency_code").asText("")),
                    p.path("categories").path(0).path("name").asText(""), tailles, couleurs,
                    p.has("is_in_stock") ? p.path("is_in_stock").asBoolean(true) : null, p.path("images").path(0).path("src").asText("")));
        }
        return produits;
    }

    // ------------------------------------------------------------ données structurées schema.org

    static List<Produit> jsonLd(ObjectMapper json, Document doc, URI base) {
        List<Produit> produits = new ArrayList<>();
        for (Element script : doc.select("script[type=application/ld+json]")) {
            try {
                chercherProduits(json.readTree(script.data().isBlank() ? script.html() : script.data()), base, produits);
            } catch (IOException | RuntimeException e) {
                // bloc illisible : on passe au suivant
            }
        }
        return produits;
    }

    private static void chercherProduits(JsonNode n, URI base, List<Produit> sortie) {
        if (n == null || sortie.size() >= CatalogueImport.PRODUITS_MAX) return;
        if (n.isArray()) {
            n.forEach(x -> chercherProduits(x, base, sortie));
            return;
        }
        if (!n.isObject()) return;
        if (estProduit(n)) {
            Produit p = produit(n, base);
            if (p != null) sortie.add(p);
        }
        for (String cle : new String[]{"@graph", "itemListElement", "item", "mainEntity", "hasVariant"}) {
            if (n.has(cle)) chercherProduits(n.get(cle), base, sortie);
        }
    }

    private static boolean estProduit(JsonNode n) {
        JsonNode t = n.path("@type");
        if (t.isArray()) {
            for (JsonNode x : t) if ("Product".equalsIgnoreCase(x.asText())) return true;
            return false;
        }
        return "Product".equalsIgnoreCase(t.asText());
    }

    private static Produit produit(JsonNode n, URI base) {
        String nom = n.path("name").asText("").trim();
        if (nom.isEmpty()) return null;
        JsonNode offre = n.path("offers");
        if (offre.isArray()) offre = offre.path(0);
        BigDecimal prix = CatalogueImport.nombre(offre.path("price").asText(""));
        if (prix == null) prix = CatalogueImport.nombre(offre.path("lowPrice").asText(""));
        String disponibilite = offre.path("availability").asText("").toLowerCase(Locale.ROOT);
        Boolean dispo = disponibilite.isEmpty() ? null : !(disponibilite.contains("outofstock") || disponibilite.contains("soldout"));
        List<String> couleurs = new ArrayList<>();
        if (n.path("color").isTextual()) couleurs.add(n.path("color").asText());
        List<String> tailles = new ArrayList<>();
        if (n.path("size").isTextual()) tailles.add(n.path("size").asText());
        String categorie = n.path("category").isTextual() ? n.path("category").asText("") : "";
        return new Produit(nom, texteBrut(n.path("description").asText("")), CatalogueImport.formaterPrix(prix, offre.path("priceCurrency").asText("")),
                categorie, tailles, couleurs, dispo, absolue(premiereImage(n.path("image")), base));
    }

    private static String premiereImage(JsonNode image) {
        if (image == null || image.isMissingNode()) return "";
        if (image.isTextual()) return image.asText();
        if (image.isArray()) return image.size() == 0 ? "" : premiereImage(image.get(0));
        return image.path("url").asText("");
    }

    private static String absolue(String url, URI base) {
        if (url == null || url.isBlank()) return null;
        try {
            return base.resolve(url.trim()).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static String texteBrut(String html) {
        if (html == null || html.isBlank()) return "";
        return Jsoup.parse(html).text().replaceAll("\\s+", " ").trim();
    }
}
