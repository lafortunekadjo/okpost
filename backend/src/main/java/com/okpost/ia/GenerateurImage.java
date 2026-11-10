package com.okpost.ia;

import java.util.UUID;

/** Génère une image à partir d'un prompt. Une implémentation par fournisseur (Gemini aujourd'hui, d'autres plus tard). */
public interface GenerateurImage {

    record ImageGeneree(byte[] octets, String modele) {}

    /** Image de référence jointe à la demande (ex. le logo du client à reproduire tel quel). */
    record Reference(byte[] octets, String mime) {}

    String nom();

    boolean configure();

    /** @param ratio 1:1, 4:5, 9:16… @param references images jointes (peut être vide) */
    ImageGeneree generer(UUID workspaceId, String prompt, String ratio, java.util.List<Reference> references);
}
