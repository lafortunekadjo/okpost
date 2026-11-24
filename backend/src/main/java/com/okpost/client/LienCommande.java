package com.okpost.client;

import com.okpost.commun.ApiException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * Lien de commande WhatsApp (wa.me) avec un message déjà écrit : le client n'a plus qu'à préciser sa taille et sa couleur.
 * Le numéro de la fiche est converti au format international ; sans indicatif connu, on refuse plutôt que de deviner.
 */
public final class LienCommande {

    private LienCommande() {}

    public record Lien(String url, String message) {}

    private static final Map<String, String> INDICATIFS = Map.ofEntries(
            Map.entry("cameroun", "237"), Map.entry("cotedivoire", "225"), Map.entry("senegal", "221"), Map.entry("gabon", "241"),
            Map.entry("congo", "242"), Map.entry("congobrazzaville", "242"), Map.entry("republiqueducongo", "242"),
            Map.entry("rdc", "243"), Map.entry("rdcongo", "243"), Map.entry("congokinshasa", "243"),
            Map.entry("republiquedemocratiqueducongo", "243"), Map.entry("benin", "229"), Map.entry("togo", "228"),
            Map.entry("mali", "223"), Map.entry("burkinafaso", "226"), Map.entry("guinee", "224"), Map.entry("niger", "227"),
            Map.entry("tchad", "235"), Map.entry("centrafrique", "236"), Map.entry("guineeequatoriale", "240"),
            Map.entry("mauritanie", "222"), Map.entry("maroc", "212"), Map.entry("tunisie", "216"), Map.entry("algerie", "213"),
            Map.entry("rwanda", "250"), Map.entry("burundi", "257"), Map.entry("madagascar", "261"),
            Map.entry("france", "33"), Map.entry("belgique", "32"), Map.entry("suisse", "41"), Map.entry("canada", "1"));

    /** Numéro WhatsApp au format international sans « + » (ex. 237677000000), ou null s'il est inutilisable. */
    public static String numero(String brut, String pays) {
        if (brut == null || brut.isBlank()) return null;
        String t = brut.trim();
        String chiffres = t.replaceAll("\\D", "");
        String resultat;
        if (t.startsWith("+")) {
            resultat = chiffres;
        } else if (chiffres.startsWith("00")) {
            resultat = chiffres.substring(2);
        } else {
            String indicatif = INDICATIFS.get(cle(pays));
            if (indicatif == null) return null;
            if (chiffres.startsWith(indicatif) && chiffres.length() >= indicatif.length() + 8) {
                resultat = chiffres;
            } else {
                resultat = indicatif + (chiffres.startsWith("0") ? chiffres.substring(1) : chiffres);
            }
        }
        return resultat.length() >= 8 && resultat.length() <= 15 ? resultat : null;
    }

    /** @param produit produit concerné, ou null pour une demande d'information générale */
    public static Lien construire(Client client, Client.Offre produit) {
        if (client.getWhatsapp() == null || client.getWhatsapp().isBlank()) {
            throw ApiException.requeteInvalide("Aucun numéro WhatsApp dans la fiche du client (onglet « En ligne »).");
        }
        String numero = numero(client.getWhatsapp(), client.getPays());
        if (numero == null) {
            throw ApiException.requeteInvalide("Le numéro WhatsApp doit commencer par l'indicatif du pays (ex. +237 6…) pour fabriquer un lien de commande.");
        }
        String message = message(client, produit);
        String url = "https://wa.me/" + numero + "?text=" + URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20");
        return new Lien(url, message);
    }

    static String message(Client client, Client.Offre produit) {
        StringBuilder sb = new StringBuilder("Bonjour ").append(client.getNom()).append(", ");
        if (produit == null) {
            return sb.append("j'ai vu votre publication et je souhaite avoir plus d'informations.").toString();
        }
        sb.append("je souhaite commander : ").append(produit.nom());
        if (produit.prix() != null && !produit.prix().isBlank()) sb.append(" (").append(produit.prix().trim()).append(')');
        sb.append('.');
        if (produit.tailles() != null && !produit.tailles().isEmpty()) sb.append("\nTaille : ");
        if (produit.couleurs() != null && !produit.couleurs().isEmpty()) sb.append("\nCouleur : ");
        return sb.append("\nMerci !").toString();
    }

    private static String cle(String pays) {
        if (pays == null) return "";
        return Normalizer.normalize(pays, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }
}
