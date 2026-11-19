package com.okpost.calendrier;

import java.util.Locale;
import java.util.regex.Pattern;

/** Corrections de petits défauts des textes générés par l'IA. */
final class NettoyageTextes {

    /** Une lettre isolée suivie d'un mot qui commence par la même lettre (« R rappeler ») : la lettre en trop est un défaut de génération. */
    private static final Pattern INITIALE_EN_TROP = Pattern.compile("^\\s*([A-Za-z])\\s+(?=\\1[a-zà-ÿ]{2})", Pattern.CASE_INSENSITIVE);

    private NettoyageTextes() {}

    /** « R rappeler aux capitaines » devient « Rappeler aux capitaines ». Les autres idées ne sont pas modifiées. */
    static String idee(String idee) {
        if (idee == null) return null;
        String propre = INITIALE_EN_TROP.matcher(idee).replaceFirst("");
        if (propre.equals(idee) || propre.isEmpty()) return idee;
        return propre.substring(0, 1).toUpperCase(Locale.ROOT) + propre.substring(1);
    }
}
