package com.okpost.admin;

import java.util.LinkedHashMap;
import java.util.Map;

/** Actions du journal d'activité et leur libellé affiché au super-admin. */
public final class Actions {

    private Actions() {}

    public static final String INSCRIPTION = "INSCRIPTION";
    public static final String CONNEXION = "CONNEXION";
    public static final String CONNEXION_ECHEC = "CONNEXION_ECHEC";
    public static final String MEMBRE_INVITE = "MEMBRE_INVITE";
    public static final String CLIENT_CREE = "CLIENT_CREE";
    public static final String CLIENT_ARCHIVE = "CLIENT_ARCHIVE";
    public static final String CALENDRIER_CREE = "CALENDRIER_CREE";
    public static final String LIEN_VALIDATION = "LIEN_VALIDATION";
    public static final String LIEN_PARTAGE = "LIEN_PARTAGE";
    public static final String VALIDATION_CLIENT = "VALIDATION_CLIENT";
    public static final String PUBLICATION_PUBLIEE = "PUBLICATION_PUBLIEE";
    public static final String PUBLICATION_ECHEC = "PUBLICATION_ECHEC";

    public static final Map<String, String> LIBELLES = libelles();

    private static Map<String, String> libelles() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(INSCRIPTION, "Création de compte");
        m.put(CONNEXION, "Connexion");
        m.put(CONNEXION_ECHEC, "Connexion refusée");
        m.put(MEMBRE_INVITE, "Membre invité");
        m.put(CLIENT_CREE, "Client créé");
        m.put(CLIENT_ARCHIVE, "Client archivé");
        m.put(CALENDRIER_CREE, "Calendrier généré");
        m.put(LIEN_VALIDATION, "Lien de validation envoyé");
        m.put(LIEN_PARTAGE, "Lien de consultation envoyé");
        m.put(VALIDATION_CLIENT, "Retour du client");
        m.put(PUBLICATION_PUBLIEE, "Publication publiée");
        m.put(PUBLICATION_ECHEC, "Échec de publication");
        return m;
    }
}
