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

/** Télécharge une image publique avec les mêmes protections que la lecture d'un site (adresses internes refusées, redirections revérifiées). */
@Component
public class TelechargeurImage {

    static final int TAILLE_MAX = 6_000_000;
    private static final int REDIRECTIONS_MAX = 4;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    byte[] telecharger(String url) {
        URI courant = SecuriteUrl.normaliser(url);
        for (int saut = 0; saut <= REDIRECTIONS_MAX; saut++) {
            SecuriteUrl.normaliser(courant.toString());
            SecuriteUrl.verifierHote(courant);
            HttpRequest requete = HttpRequest.newBuilder(courant)
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0 (compatible; OkPostBot/1.0; import d'images demandé par le propriétaire du site)")
                    .header("Accept", "image/*")
                    .GET().build();
            HttpResponse<InputStream> reponse;
            try {
                reponse = http.send(requete, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Téléchargement interrompu");
            } catch (SSLException e) {
                throw ApiException.requeteInvalide("Certificat de sécurité invalide");
            } catch (IOException | RuntimeException e) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Le serveur de l'image ne répond pas");
            }
            int code = reponse.statusCode();
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
            if (code >= 400) {
                fermer(reponse);
                throw ApiException.requeteInvalide("Le site refuse le téléchargement de l'image (" + code + ")");
            }
            String type = reponse.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
            if (!type.startsWith("image/") || type.contains("svg")) {
                fermer(reponse);
                throw ApiException.requeteInvalide("Ce n'est pas une image PNG, JPG ou WebP");
            }
            try (InputStream in = reponse.body()) {
                byte[] octets = in.readNBytes(TAILLE_MAX + 1);
                if (octets.length > TAILLE_MAX) throw ApiException.requeteInvalide("Image trop lourde (6 Mo maximum)");
                return octets;
            } catch (IOException e) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Téléchargement interrompu");
            }
        }
        throw ApiException.requeteInvalide("Trop de redirections");
    }

    private static void fermer(HttpResponse<InputStream> reponse) {
        try {
            reponse.body().close();
        } catch (IOException ignore) {
            // la connexion est abandonnée
        }
    }
}
