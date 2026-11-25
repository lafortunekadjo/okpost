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
        ligne(sb, "Type d'activité", switch (c.getTypeActivite() == null ? "SERVICES" : c.getTypeActivite()) {
            case "PRODUITS" -> "vente de produits (boutique)";
            case "MIXTE" -> "vente de produits et de services";
            default -> "prestations de services";
        });
        ligne(sb, "Slogan", c.getSlogan());
        ligne(sb, "Année de création", c.getAnneeCreation() == null ? null : c.getAnneeCreation().toString());
        ligne(sb, "Ville", c.getVille());
        ligne(sb, "Pays", c.getPays());
        ligne(sb, "Zone desservie", c.getZone());
        ligne(sb, "Adresse", c.getAdresse());
        ligne(sb, "Horaires", c.getHoraires());
        if (c.getOffres() != null && !c.getOffres().isEmpty()) {
            ligne(sb, "Devise des prix", c.getDevise());
            sb.append("Offres :\n");
            int affichees = 0;
            for (Client.Offre o : c.getOffres()) {
                if (affichees++ >= OFFRES_DANS_LE_PROMPT) break;
                sb.append("  - ").append(o.nom());
                String prix = com.okpost.client.Prix.avecDevise(o.prix(), c.getDevise());
                if (prix != null) sb.append(" — ").append(prix);
                if (o.description() != null && !o.description().isBlank()) sb.append(" : ").append(o.description());
                if (o.categorie() != null && !o.categorie().isBlank()) sb.append(" [catégorie : ").append(o.categorie().trim()).append(']');
                if (o.tailles() != null && !o.tailles().isEmpty()) sb.append(" [tailles : ").append(String.join(", ", o.tailles())).append(']');
                if (o.couleurs() != null && !o.couleurs().isEmpty()) sb.append(" [couleurs : ").append(String.join(", ", o.couleurs())).append(']');
                if (o.variantes() != null && !o.variantes().isBlank()) sb.append(" [variantes : ").append(o.variantes().trim()).append(']');
                if (Boolean.FALSE.equals(o.disponible())) sb.append(" [EN RUPTURE]");
                if (c.vendDesProduits()) sb.append(o.photos() != null && !o.photos().isEmpty() ? " [photo du produit disponible]" : " [pas de photo du produit]");
                sb.append('\n');
            }
            if (c.getOffres().size() > OFFRES_DANS_LE_PROMPT) {
                sb.append("  … et ").append(c.getOffres().size() - OFFRES_DANS_LE_PROMPT).append(" autres produits (ne cite que ceux de la liste ci-dessus)\n");
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
        if (c.vendDesProduits()) sb.append(REGLES_BOUTIQUE);
        return sb.toString();
    }

    /** Un catalogue de plusieurs centaines de produits ne tient pas dans un prompt : les premiers sont cités. */
    static final int OFFRES_DANS_LE_PROMPT = 40;

    static final String REGLES_BOUTIQUE = """

            RÈGLES POUR UNE ENTREPRISE QUI VEND DES PRODUITS
            - Chaque publication de vente porte sur un produit précis de la liste « Offres » : mets son nom EXACT dans briefVisuel.produit (null si la publication ne parle d'aucun produit précis : conseil, coulisses, avis, livraison…).
            - Pour une publication sur un produit dont la photo est disponible, choisis le gabarit PRODUIT (ou NOUVEAUTE pour un arrivage, PROMO pour une remise réelle). La photo réelle du produit sera utilisée : n'invente ni son apparence ni ses couleurs.
            - Pour simplement montrer un produit ou un modèle (mannequin, packshot) sans texte sur l'image, choisis PHOTO_SEULE : la photo réelle est publiée avec le logo en filigrane ; la légende doit alors donner le nom, le prix et l'appel à la commande.
            - Pour présenter plusieurs produits ensemble (sélection, gamme, idée cadeau), choisis COLLECTION avec 2 à 4 produits qui ont une photo.
            - Pour des publications de vente, propose régulièrement un appel à l'action de commande par WhatsApp : OkPost peut fournir un lien de commande déjà rédigé pour chaque produit.
            - Si le produit n'a pas de photo, le promptImage ne montre JAMAIS le produit lui-même (ni vêtement, ni article) : seulement une ambiance, un lieu ou un geste, sans corps dénudé ni pose suggestive.
            - Prix, tailles, couleurs et disponibilités : uniquement ceux de la liste « Offres ». Ne promets ni remise, ni stock, ni délai qui n'y figurent pas.
            - Un produit marqué [EN RUPTURE] n'est jamais présenté comme disponible : tu peux annoncer son retour seulement si la fiche le dit, ou l'ignorer.
            - Quand tailles ou couleurs sont listées, tu peux les citer (« du 85 au 100 », « en noir et nude ») mais jamais en inventer.
            - Varie les angles : nouveauté, conseil de taille ou d'entretien, produit en situation, confiance et confort, promotion réelle, dernières pièces, livraison et paiement, coulisses de la boutique, réponse à une question fréquente.
            - Appel à l'action : la commande se fait par message. Si un numéro WhatsApp est renseigné, utilise « Commander sur WhatsApp » ou « Écris-nous sur WhatsApp ».
            - Ton élégant et respectueux, conforme aux règles des réseaux sociaux : pour les produits intimes ou de lingerie, parle de matière, de confort, de coupe, de taille et de confiance en soi, sans vocabulaire ni mise en scène suggestifs.
            """;

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
