package com.okpost.client;

import org.springframework.core.io.Resource;

import java.io.InputStream;

/** Stockage des logos et photos ; disque du VPS pour le MVP, S3 plus tard. */
public interface StockageFichiers {
    String enregistrer(InputStream contenu, String extension);
    Resource lire(String chemin);
    void supprimer(String chemin);
}
