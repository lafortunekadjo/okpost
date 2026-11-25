package com.okpost.client;

/** Prix affichés : la devise de la fiche est ajoutée quand le prix n'en porte pas (« 12 000 » devient « 12 000 FCFA »). */
public final class Prix {

    private Prix() {}

    public static final String DEVISE_PAR_DEFAUT = "FCFA";

    /**
     * @return le prix tel quel s'il est vide ou s'il contient déjà une devise (lettres ou symbole € $ £), sinon le prix suivi de la devise
     */
    public static String avecDevise(String prix, String devise) {
        if (prix == null || prix.isBlank()) return null;
        String p = prix.trim();
        if (devise == null || devise.isBlank()) return p;
        if (p.chars().anyMatch(c -> Character.isLetter(c) || c == '€' || c == '$' || c == '£')) return p;
        return p + " " + devise.trim();
    }
}
