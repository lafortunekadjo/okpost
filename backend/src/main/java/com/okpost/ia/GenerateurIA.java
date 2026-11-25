package com.okpost.ia;

import com.okpost.ia.Modeles.DemandeCalendrier;
import com.okpost.ia.Modeles.DetailGenere;
import com.okpost.ia.Modeles.GrilleGeneree;
import com.okpost.ia.Modeles.IdeeADetailler;

import java.util.List;
import java.util.UUID;

/** Point d'entrée unique de la génération ; une implémentation par fournisseur (Gemini, Claude). */
public interface GenerateurIA {

    String nom();

    boolean configure();

    /** Modèle utilisé pour la grille, affiché avec les notes de qualité. */
    default String modele() { return nom(); }

    GrilleGeneree genererCalendrier(UUID workspaceId, String ficheClient, DemandeCalendrier demande);

    /** Détaille un lot d'idées (légende, hashtags, CTA, brief visuel ou scénario vidéo). */
    List<DetailGenere> genererDetails(UUID workspaceId, String ficheClient, String langue, List<IdeeADetailler> idees);

    /** Temps forts propres au métier du client, en plus du catalogue. Dates à vérifier par le CM. */
    default List<com.okpost.ia.Modeles.TempsFortSuggere> suggererTempsForts(UUID workspaceId, String ficheClient,
            java.time.LocalDate debut, java.time.LocalDate fin, List<String> dejaListes) {
        return List.of();
    }

    /** Lecture du bilan d'une période et recommandations pour la suivante. */
    default com.okpost.ia.Modeles.Recommandations recommandations(UUID workspaceId, String ficheClient, String bilan) {
        throw new GenerationException("Recommandations non disponibles avec ce fournisseur");
    }

    /** Déduit une fiche client du texte d'un site web (déjà extrait et nettoyé). */
    default com.okpost.ia.Modeles.FicheSite analyserSite(UUID workspaceId, String contenuSite) {
        throw new GenerationException("Lecture de site non disponible avec ce fournisseur");
    }

    /** Extrait les produits d'un texte (pages d'un site, publications) : blocs numérotés [n], aucun produit inventé. */
    default com.okpost.ia.Modeles.ProduitsExtraits extraireProduits(UUID workspaceId, String contenu) {
        throw new GenerationException("Extraction de produits non disponible avec ce fournisseur");
    }

    /** Corrige, reformule ou raccourcit un texte de la fiche client, sans rien inventer. */
    default String reformulerTexte(UUID workspaceId, String ficheClient, String langue, String texte, String sujet,
                                   String instruction, int longueurMax) {
        throw new GenerationException("Reformulation non disponible avec ce fournisseur");
    }

    String regenererChamp(UUID workspaceId, String ficheClient, String langue, String publication, String champ, String consigne);
}
