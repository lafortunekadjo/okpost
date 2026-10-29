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

    GrilleGeneree genererCalendrier(UUID workspaceId, String ficheClient, DemandeCalendrier demande);

    /** Détaille un lot d'idées (légende, hashtags, CTA, brief visuel ou scénario vidéo). */
    List<DetailGenere> genererDetails(UUID workspaceId, String ficheClient, String langue, List<IdeeADetailler> idees);

    String regenererChamp(UUID workspaceId, String ficheClient, String langue, String publication, String champ, String consigne);
}
