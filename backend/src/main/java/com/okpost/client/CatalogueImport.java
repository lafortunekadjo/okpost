package com.okpost.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Types et outils communs aux imports de catalogue (site web, Facebook, Instagram). */
public final class CatalogueImport {

    private CatalogueImport() {}

    public static final int PRODUITS_MAX = 100;

    /** Produit proposé : à relire avant d'être ajouté au catalogue. imageUrl sert à importer la photo dans la banque d'images. */
    public record Produit(String nom, String description, String prix, String categorie, List<String> tailles,
                          List<String> couleurs, Boolean disponible, String imageUrl) {}

    /** source : d'où viennent les produits (« Boutique Shopify », « Facebook »…) ; avertissements : à lire avant d'importer. */
    public record Resultat(List<Produit> produits, String source, List<String> avertissements) {}

    /** Sans doublon de nom, 100 produits au plus, textes plafonnés. */
    public static List<Produit> nettoyer(List<Produit> bruts) {
        Map<String, Produit> uniques = new LinkedHashMap<>();
        for (Produit p : bruts) {
            if (p == null || p.nom() == null || p.nom().isBlank()) continue;
            String cle = cle(p.nom());
            if (cle.isEmpty() || uniques.containsKey(cle)) continue;
            uniques.put(cle, new Produit(max(p.nom(), 150), max(p.description(), 500), max(p.prix(), 100), max(p.categorie(), 60),
                    liste(p.tailles()), liste(p.couleurs()), p.disponible(), imageValide(p.imageUrl())));
            if (uniques.size() >= PRODUITS_MAX) break;
        }
        return new ArrayList<>(uniques.values());
    }

    static String cle(String nom) {
        return Normalizer.normalize(nom, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String max(String s, int taille) {
        if (s == null) return null;
        String t = s.trim().replaceAll("[\\p{Cntrl}&&[^\\n]]", "");
        if (t.isEmpty()) return null;
        return t.length() > taille ? t.substring(0, taille).trim() : t;
    }

    private static List<String> liste(List<String> valeurs) {
        if (valeurs == null) return null;
        List<String> l = valeurs.stream().map(v -> max(v, 20)).filter(java.util.Objects::nonNull).distinct().limit(20).toList();
        return l.isEmpty() ? null : l;
    }

    /** Seules les adresses http(s) sont gardées : l'adresse sera téléchargée plus tard, avec ses propres contrôles. */
    private static String imageValide(String url) {
        if (url == null) return null;
        String u = url.trim();
        if (u.startsWith("//")) u = "https:" + u;
        return u.matches("(?i)^https?://\\S{4,1500}$") ? u : null;
    }

    // ------------------------------------------------------------ prix

    /** « 12000.00 » + XAF devient « 12 000 FCFA » ; sans devise connue, juste le nombre groupé. */
    public static String formaterPrix(BigDecimal montant, String devise) {
        if (montant == null || montant.signum() < 0) return null;
        BigDecimal m = montant.stripTrailingZeros();
        if (m.scale() < 0) m = m.setScale(0, RoundingMode.UNNECESSARY);
        if (m.scale() > 0) m = m.setScale(2, RoundingMode.HALF_UP);
        String[] parties = m.toPlainString().split("\\.");
        String entier = parties[0].replaceAll("(?<=\\d)(?=(\\d{3})+$)", " ");
        String texte = parties.length > 1 ? entier + "," + parties[1] : entier;
        String symbole = symbole(devise);
        return symbole == null ? texte : texte + " " + symbole;
    }

    static String symbole(String devise) {
        if (devise == null || devise.isBlank()) return null;
        return switch (devise.trim().toUpperCase(Locale.ROOT)) {
            case "XAF", "XOF" -> "FCFA";
            case "EUR" -> "€";
            case "USD" -> "$";
            case "GBP" -> "£";
            case "MAD" -> "MAD";
            default -> devise.trim().toUpperCase(Locale.ROOT);
        };
    }

    /** Lit « 12000 », « 12 000,50 » ou « 12000.00 » ; null si ce n'est pas un nombre. */
    public static BigDecimal nombre(String texte) {
        if (texte == null) return null;
        String t = texte.replaceAll("[^0-9,.]", "");
        if (t.isEmpty()) return null;
        if (t.contains(",") && t.contains(".")) t = t.replace(",", "");
        else if (t.contains(",")) t = t.replace(",", ".");
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
