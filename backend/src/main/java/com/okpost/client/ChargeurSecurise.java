package com.okpost.client;

import com.okpost.commun.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * Télécharge une ressource publique (JSON, texte) avec les mêmes protections que la lecture d'un site : seules les adresses publiques
 * en http(s) sont suivies, chaque redirection est revérifiée, la taille et la durée sont plafonnées.
 */
@Component
public class ChargeurSecurise {

    private static final int REDIRECTIONS_MAX = 4;

    /** statut 200 à 399 final ; octets vides pour une réponse en erreur. */
    public record Reponse(int statut, String type, byte[] octets) {
        public boolean ok() { return statut >= 200 && statut < 300; }
        public String texte() { return new String(octets, java.nio.charset.StandardCharsets.UTF_8); }
    }

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /** @param accept type attendu (ex. application/json) ; une réponse d'un autre type n'est pas lue. */
    public Reponse get(URI depart, String accept, int maxOctets, Duration delai) {
        URI courant = depart;
        for (int saut = 0; saut <= REDIRECTIONS_MAX; saut++) {
            SecuriteUrl.normaliser(courant.toString());
            SecuriteUrl.verifierHote(courant);
            HttpRequest requete = HttpRequest.newBuilder(courant)
                    .timeout(delai)
                    .header("User-Agent", "Mozilla/5.0 (compatible; OkPostBot/1.0; lecture d'un catalogue fourni par son propriétaire)")
                    .header("Accept", accept)
                    .header("Accept-Language", "fr,en;q=0.8")
                    .GET().build();
            HttpResponse<InputStream> reponse;
            try {
                reponse = http.send(requete, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Lecture interrompue");
            } catch (SSLException e) {
                throw ApiException.requeteInvalide("Le certificat de sécurité du site est invalide");
            } catch (IOException | RuntimeException e) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Le site ne répond pas ou est trop lent");
            }
            int code = reponse.statusCode();
            String type = reponse.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
            if (code >= 300 && code < 400) {
                String lieu = reponse.headers().firstValue("Location").orElse(null);
                fermer(reponse);
                if (lieu == null) throw ApiException.requeteInvalide("Redirection invalide");
                try {
                    courant = courant.resolve(lieu);
                } catch (IllegalArgumentException e) {
                    throw ApiException.requeteInvalide("Redirection invalide");
                }
                continue;
            }
            if (code >= 400 || !accepte(type, accept)) {
                fermer(reponse);
                return new Reponse(code >= 400 ? code : 415, type, new byte[0]);
            }
            try (InputStream in = reponse.body()) {
                byte[] octets = in.readNBytes(maxOctets + 1);
                if (octets.length > maxOctets) throw ApiException.requeteInvalide("Réponse trop volumineuse");
                return new Reponse(code, type, octets);
            } catch (IOException e) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Lecture interrompue");
            }
        }
        throw ApiException.requeteInvalide("Trop de redirections");
    }

    private static boolean accepte(String type, String accept) {
        if (type.isEmpty()) return true;
        if (accept.contains("json")) return type.contains("json");
        return true;
    }

    private static void fermer(HttpResponse<InputStream> reponse) {
        try {
            reponse.body().close();
        } catch (IOException ignore) {
            // connexion abandonnée
        }
    }
}
