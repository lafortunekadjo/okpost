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

    private static final Pattern HEURE = Pattern.compile("^([01]?\\d|2[0-3])[:hH]([0-5]\\d)$");

    /** « 9:05 », « 18h30 » ou « 18:30 » → « 09:05 », « 18:30 » ; null si ce n'est pas une heure valide. */
    static String heure(String h) {
        if (h == null) return null;
        java.util.regex.Matcher m = HEURE.matcher(h.trim());
        return m.matches() ? String.format(Locale.ROOT, "%02d:%s", Integer.parseInt(m.group(1)), m.group(2)) : null;
    }

    /** Heure proposée par l'IA : retenue seulement si elle est plausible pour un réseau social (6 h à 22 h 30). */
    static String heureIa(String h) {
        String propre = heure(h);
        if (propre == null) return null;
        int minutes = Integer.parseInt(propre.substring(0, 2)) * 60 + Integer.parseInt(propre.substring(3));
        return minutes >= 6 * 60 && minutes <= 22 * 60 + 30 ? propre : null;
    }
}
