package com.okpost.ia;

import com.fasterxml.jackson.databind.JsonNode;
import com.okpost.config.OkpostProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Images gratuites (dans la limite du quota quotidien) avec FLUX.1 schnell sur Cloudflare Workers AI.
 * Images carrées, recadrées ensuite au format demandé ; le texte dans l'image est moins fiable qu'avec Gemini.
 */
@Component
public class GenerateurImageCloudflare implements GenerateurImage {

    private static final Logger log = LoggerFactory.getLogger(GenerateurImageCloudflare.class);

    private final RestClient http;
    private final String compte;
    private final String jeton;
    private final String modele;
    private final Journal journal;

    public GenerateurImageCloudflare(OkpostProperties props, Journal journal) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(15));
        f.setReadTimeout(Duration.ofSeconds(90));
        this.http = RestClient.builder().requestFactory(f).baseUrl("https://api.cloudflare.com/client/v4").build();
        OkpostProperties.Image img = props.ia().image();
        this.compte = IaConfig.propre(img == null ? null : img.cloudflareCompte(), "");
        this.jeton = IaConfig.propre(img == null ? null : img.cloudflareJeton(), "");
        this.modele = IaConfig.propre(img == null ? null : img.cloudflareModele(), "@cf/black-forest-labs/flux-1-schnell");
        this.journal = journal;
        log.info("Moteur d'images Cloudflare : {}", configure()
                ? "configuré (" + modele + ")" : "non configuré — CLOUDFLARE_ACCOUNT_ID ou CLOUDFLARE_API_TOKEN absent");
    }

    @Override
    public String nom() { return "cloudflare"; }

    @Override
    public boolean configure() { return !compte.isEmpty() && !jeton.isEmpty(); }

    @Override
    public ImageGeneree generer(UUID ws, String prompt, String ratio, List<Reference> references) {
        if (!configure()) {
            throw new GenerationException("Cloudflare n'est pas configuré (CLOUDFLARE_ACCOUNT_ID et CLOUDFLARE_API_TOKEN)");
        }
        String texte = prompt.length() > 2000 ? prompt.substring(0, 2000) : prompt;
        long debut = System.currentTimeMillis();
        log.info("Image cloudflare / {} — envoi de la demande", modele);
        try {
            JsonNode rep = http.post().uri("/accounts/{compte}/ai/run/" + modele, compte)
                    .header("Authorization", "Bearer " + jeton)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("prompt", texte, "steps", 8))
                    .retrieve()
                    .body(JsonNode.class);
            String base64 = rep == null ? "" : rep.path("result").path("image").asText(rep.path("image").asText(""));
            if (base64.isEmpty()) {
                throw new GenerationException("Cloudflare n'a pas renvoyé d'image"
                        + (rep != null && rep.has("errors") ? " : " + rep.get("errors") : ""));
            }
            journal.noter(ws, "cloudflare", modele, "image", null, null, System.currentTimeMillis() - debut, true, null);
            log.info("Image cloudflare / {} — reçue en {} s", modele, (System.currentTimeMillis() - debut) / 1000);
            return new ImageGeneree(Base64.getDecoder().decode(base64), modele);
        } catch (RestClientResponseException e) {
            String detail = e.getResponseBodyAsString();
            journal.noter(ws, "cloudflare", modele, "image", null, null, System.currentTimeMillis() - debut, false, detail);
            log.warn("Image cloudflare / {} — erreur {} : {}", modele, e.getStatusCode().value(), detail);
            if (e.getStatusCode().value() == 429 || detail.toLowerCase().contains("neurons")) {
                throw new GenerationException("Quota gratuit Cloudflare du jour atteint. Réessaie demain ou utilise Gemini.", e);
            }
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                throw new GenerationException("Jeton Cloudflare refusé : vérifie qu'il a la permission « Workers AI ».", e);
            }
            throw new GenerationException("Erreur Cloudflare (" + e.getStatusCode().value() + ")", e);
        } catch (GenerationException e) {
            throw e;
        } catch (RuntimeException e) {
            journal.noter(ws, "cloudflare", modele, "image", null, null, System.currentTimeMillis() - debut, false, e.getMessage());
            throw new GenerationException("Cloudflare ne répond pas. Vérifie ta connexion et réessaie.", e);
        }
    }
}
