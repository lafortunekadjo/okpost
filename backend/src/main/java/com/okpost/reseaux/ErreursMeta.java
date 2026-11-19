package com.okpost.reseaux;

import java.time.Duration;
import java.util.Locale;

/**
 * Range une erreur de publication dans une catégorie pour décider quoi faire :
 * réessayer plus tard (panne passagère), ou s'arrêter tout de suite et prévenir (accès perdu, média refusé).
 */
final class ErreursMeta {

    enum Nature {
        /** Jeton expiré ou révoqué, permission retirée : seule une reconnexion du compte règle le problème. */
        ACCES,
        /** Le média ou la demande est refusé (format, ratio…) : réessayer à l'identique ne sert à rien. */
        DEFINITIVE,
        /** Panne passagère de Meta ou du réseau, limite de débit : un nouvel essai peut réussir. */
        TEMPORAIRE
    }

    private ErreursMeta() {}

    static Nature classer(String message) {
        String m = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (contient(m, "(#190)", "(#102)", "(#463)", "(#467)", "(#10)", "(#200)", "(#283)", "access token", "oauthexception",
                "session has expired", "invalid oauth", "permission", "token")) {
            return Nature.ACCES;
        }
        if (contient(m, "temporarily", "try again", "timeout", "timed out", "trop de temps", "erreur 5", "erreur 429",
                "service unavailable", "i/o error", "connection", "unexpected error", "unknown error", "rate limit",
                "too many calls", "(#1)", "(#2)", "(#4)", "(#17)", "(#32)", "(#341)")) {
            return Nature.TEMPORAIRE;
        }
        if (contient(m, "aspect ratio", "only photo or video", "refusé le média", "not supported", "unsupported",
                "invalid parameter", "image illisible", "n'accepte que", "n'a pas été accepté", "pas encore montée", "n'est pas encore")) {
            return Nature.DEFINITIVE;
        }
        return Nature.TEMPORAIRE;
    }

    /** Attente avant le nouvel essai : 5 minutes, puis 20 minutes, puis 1 heure. */
    static Duration delai(int tentatives) {
        return tentatives <= 1 ? Duration.ofMinutes(5) : tentatives == 2 ? Duration.ofMinutes(20) : Duration.ofHours(1);
    }

    private static boolean contient(String texte, String... motifs) {
        for (String motif : motifs) if (texte.contains(motif)) return true;
        return false;
    }
}
