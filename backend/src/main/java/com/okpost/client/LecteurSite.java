package com.okpost.client;

import com.okpost.client.ExtracteurSite.ContenuSite;
import com.okpost.client.ExtracteurSite.Page;
import com.okpost.commun.ApiException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lit le site d'un client : sa page d'accueil et jusqu'à 3 pages utiles (offres, présentation, contact).
 * Chaque adresse suivie, y compris après une redirection, est revérifiée (SecuriteUrl) ; la taille et la durée sont plafonnées.
 */
@Component
public class LecteurSite {

    private static final Logger log = LoggerFactory.getLogger(LecteurSite.class);
    static final int TAILLE_MAX = 1_500_000;
    private static final int REDIRECTIONS_MAX = 4;
    private static final Duration BUDGET = Duration.ofSeconds(25);
    private static final Pattern CHARSET = Pattern.compile("(?i)charset=([\\w-]+)");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER) // les redirections sont suivies à la main, avec contrôle
            .build();

    public ContenuSite lire(String saisie) {
        URI depart = SecuriteUrl.normaliser(saisie);
        long fin = System.nanoTime() + BUDGET.toNanos();
        Page accueil = telecharger(depart);
        List<Page> pages = new ArrayList<>(List.of(accueil));
        for (String lien : ExtracteurSite.pagesUtiles(accueil.doc(), URI.create(accueil.url()))) {
            if (System.nanoTime() > fin) break;
            try {
                pages.add(telecharger(URI.create(lien)));
            } catch (RuntimeException e) {
                log.info("Page {} ignorée : {}", lien, e.getMessage());
            }
        }
        return ExtracteurSite.extraire(pages);
    }

    Page telecharger(URI depart) {
        URI courant = depart;
        for (int saut = 0; saut <= REDIRECTIONS_MAX; saut++) {
            SecuriteUrl.normaliser(courant.toString());
            SecuriteUrl.verifierHote(courant);
            HttpRequest requete = HttpRequest.newBuilder(courant)
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Mozilla/5.0 (compatible; OkPostBot/1.0; lecture d'un site fourni par son propriétaire)")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "fr,en;q=0.8")
                    .GET().build();
            HttpResponse<InputStream> reponse;
            try {
                reponse = http.send(requete, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Lecture du site interrompue");
            } catch (SSLException e) {
                throw ApiException.requeteInvalide("Le certificat de sécurité du site est invalide : impossible de le lire");
            } catch (IOException | RuntimeException e) {
                throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                        "Impossible de lire le site (il ne répond pas ou est trop lent)");
            }
            int code = reponse.statusCode();
            if (code >= 300 && code < 400) {
                String lieu = reponse.headers().firstValue("Location").orElse(null);
                fermer(reponse);
                if (lieu == null) throw ApiException.requeteInvalide("Le site redirige vers une adresse invalide");
                try {
                    courant = courant.resolve(lieu);
                } catch (IllegalArgumentException e) {
                    throw ApiException.requeteInvalide("Le site redirige vers une adresse invalide");
                }
                continue;
            }
            if (code >= 400) {
                fermer(reponse);
                throw ApiException.requeteInvalide("Le site a répondu « " + code + " » : il bloque peut-être la lecture automatique");
            }
            String type = reponse.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
            if (!type.isEmpty() && !type.contains("html")) {
                fermer(reponse);
                throw ApiException.requeteInvalide("Cette adresse ne mène pas à une page web");
            }
            byte[] octets;
            try (InputStream in = reponse.body()) {
                octets = in.readNBytes(TAILLE_MAX);
            } catch (IOException e) {
                throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Lecture du site interrompue");
            }
            Matcher m = CHARSET.matcher(type);
            Document doc;
            try {
                doc = Jsoup.parse(new ByteArrayInputStream(octets), m.find() ? m.group(1) : null, courant.toString());
            } catch (IOException | RuntimeException e) {
                throw ApiException.requeteInvalide("Le contenu du site est illisible");
            }
            return new Page(courant.toString(), doc);
        }
        throw ApiException.requeteInvalide("Le site redirige trop de fois");
    }

    private static void fermer(HttpResponse<InputStream> reponse) {
        try {
            reponse.body().close();
        } catch (IOException ignore) {
            // rien à faire : la connexion est abandonnée
        }
    }
}
