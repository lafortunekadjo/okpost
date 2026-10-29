package com.okpost.calendrier;

import com.okpost.client.Client;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Met la fiche client en texte pour le prompt. Elle est identique d'un appel à l'autre (mise en cache possible). */
public final class FicheTexte {

    private FicheTexte() {}

    public static String de(Client c) {
        StringBuilder sb = new StringBuilder();
        ligne(sb, "Nom", c.getNom());
        ligne(sb, "Secteur", c.getSecteur());
        ligne(sb, "Description", c.getDescription());
        ligne(sb, "Slogan", c.getSlogan());
        ligne(sb, "Année de création", c.getAnneeCreation() == null ? null : c.getAnneeCreation().toString());
        ligne(sb, "Ville", c.getVille());
        ligne(sb, "Pays", c.getPays());
        ligne(sb, "Zone desservie", c.getZone());
        ligne(sb, "Adresse", c.getAdresse());
        ligne(sb, "Horaires", c.getHoraires());
        if (c.getOffres() != null && !c.getOffres().isEmpty()) {
            sb.append("Offres :\n");
            for (Client.Offre o : c.getOffres()) {
                sb.append("  - ").append(o.nom());
                if (o.prix() != null && !o.prix().isBlank()) sb.append(" — ").append(o.prix());
                if (o.description() != null && !o.description().isBlank()) sb.append(" : ").append(o.description());
                sb.append('\n');
            }
        }
        liste(sb, "Cibles", c.getCibles());
        liste(sb, "Concurrents", c.getConcurrents());
        ligne(sb, "Ton", c.getTon());
        ligne(sb, "Adresse au public", c.isTutoiement() ? "tutoiement" : "vouvoiement");
        ligne(sb, "Langue", "en".equals(c.getLangue()) ? "anglais" : "français");
        ligne(sb, "Mots ou sujets à éviter", c.getMotsAEviter());
        ligne(sb, "Exemples de publications appréciées", c.getExemples());
        liste(sb, "Couleurs de la marque", c.getCouleurs());
        ligne(sb, "Polices", c.getPolices());
        ligne(sb, "Site web", c.getDomaine());
        ligne(sb, "WhatsApp", c.getWhatsapp());
        Map<String, String> reseaux = c.getReseaux();
        if (reseaux != null && !reseaux.isEmpty()) {
            ligne(sb, "Réseaux", reseaux.entrySet().stream().map(e -> e.getKey() + " " + e.getValue())
                    .collect(Collectors.joining(", ")));
        }
        return sb.toString();
    }

    private static void ligne(StringBuilder sb, String libelle, String valeur) {
        if (valeur != null && !valeur.isBlank()) {
            sb.append(libelle).append(" : ").append(valeur.trim()).append('\n');
        }
    }

    private static void liste(StringBuilder sb, String libelle, List<String> valeurs) {
        if (valeurs != null && !valeurs.isEmpty()) {
            ligne(sb, libelle, String.join(", ", valeurs));
        }
    }
}
