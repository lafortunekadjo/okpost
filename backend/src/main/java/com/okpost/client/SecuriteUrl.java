package com.okpost.client;

import com.okpost.commun.ApiException;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Protège le serveur quand il va lire un site dont l'adresse est saisie par un utilisateur (SSRF) :
 * seules les adresses publiques en http(s) sont acceptées, jamais le réseau interne, le serveur lui-même
 * ni les services de métadonnées du cloud (169.254.169.254).
 */
final class SecuriteUrl {

    private SecuriteUrl() {}

    /** « exemple.com/page » → https://exemple.com/page. Refuse les autres protocoles, les identifiants et les ports inhabituels. */
    static URI normaliser(String saisie) {
        if (saisie == null || saisie.isBlank()) throw ApiException.requeteInvalide("Indique l'adresse du site web");
        String s = saisie.trim();
        if (!s.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) s = "https://" + s;
        URI uri;
        try {
            uri = new URI(s);
        } catch (java.net.URISyntaxException e) {
            throw ApiException.requeteInvalide("Adresse de site invalide");
        }
        String protocole = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!protocole.equals("http") && !protocole.equals("https")) {
            throw ApiException.requeteInvalide("Seules les adresses http et https sont acceptées");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) throw ApiException.requeteInvalide("Adresse de site invalide");
        if (uri.getUserInfo() != null) throw ApiException.requeteInvalide("L'adresse ne doit pas contenir d'identifiant");
        if (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443) {
            throw ApiException.requeteInvalide("Port non autorisé : utilise l'adresse habituelle du site");
        }
        String hote = uri.getHost().toLowerCase(Locale.ROOT);
        if (hote.equals("localhost") || hote.endsWith(".localhost") || hote.endsWith(".local") || hote.endsWith(".internal")
                || hote.endsWith(".lan") || !hote.contains(".") && !hote.contains(":")) {
            throw ApiException.requeteInvalide("Cette adresse n'est pas un site public");
        }
        return uri;
    }

    /** Résout le nom de domaine : si UNE adresse obtenue est interne, la lecture est refusée. */
    static void verifierHote(URI uri) {
        InetAddress[] adresses;
        try {
            adresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException e) {
            throw ApiException.requeteInvalide("Site introuvable : vérifie l'adresse");
        }
        for (InetAddress a : adresses) {
            if (adresseInterdite(a)) throw ApiException.requeteInvalide("Cette adresse n'est pas un site public");
        }
    }

    static boolean adresseInterdite(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] o = a.getAddress();
        if (o.length == 4) {
            int a0 = o[0] & 0xFF;
            int a1 = o[1] & 0xFF;
            return a0 == 0                                  // 0.0.0.0/8
                    || (a0 == 100 && a1 >= 64 && a1 <= 127) // 100.64.0.0/10 : partage d'adresses des opérateurs
                    || (a0 == 192 && a1 == 0 && (o[2] & 0xFF) == 0) // 192.0.0.0/24
                    || (a0 == 198 && (a1 == 18 || a1 == 19)) // 198.18.0.0/15 : tests de performance
                    || a0 >= 240;                            // réservé
        }
        return (o[0] & 0xFE) == 0xFC; // fc00::/7 : adresses locales uniques IPv6
    }
}
