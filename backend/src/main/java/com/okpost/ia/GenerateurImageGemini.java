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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Images Gemini (API Interactions de Google). Payant : la facturation doit être activée sur la clé. */
@Component
public class GenerateurImageGemini implements GenerateurImage {

    private static final Logger log = LoggerFactory.getLogger(GenerateurImageGemini.class);
    private static final Set<String> RATIOS = Set.of("1:1", "4:5", "5:4", "3:4", "4:3", "9:16", "16:9", "2:3", "3:2");

    private static final String REVISION = "2026-05-20";
    private static final long PAUSE_MS = 3000;

    private final RestClient http;
    private final long delaiMaxMs;
    private final String cle;
    private final String modele;
    private final String taille;
    private final Journal journal;

    public GenerateurImageGemini(OkpostProperties props, Journal journal) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(15));
        this.delaiMaxMs = Math.max(60, props.ia().delaiSecondes()) * 1000L;
        f.setReadTimeout(Duration.ofSeconds(30));
        this.http = RestClient.builder().requestFactory(f)
                .baseUrl("https://generativelanguage.googleapis.com/v1beta").build();
        this.cle = IaConfig.propre(props.ia().gemini().apiKey(), "");
        OkpostProperties.Image img = props.ia().image();
        this.modele = IaConfig.propre(img == null ? null : img.modele(), "gemini-nano-banana-2.1");
        this.taille = IaConfig.propre(img == null ? null : img.taille(), "1K");
        this.journal = journal;
        log.info("Moteur d'images Gemini : {}", configure() ? "configuré (" + modele + ")" : "non configuré");
    }

    @Override
    public String nom() { return "gemini"; }

    @Override
    public boolean configure() { return !cle.isEmpty(); }

    @Override
    public ImageGeneree generer(UUID ws, String prompt, String ratio, List<Reference> references) {
        if (!configure()) {
            throw new GenerationException("La génération d'images n'est pas configurée (clé Gemini absente)");
        }
        // Mode « arrière-plan » : réponse immédiate avec un identifiant, puis interrogations courtes.
        // Évite les connexions longues et silencieuses que certains réseaux coupent.
        Map<String, Object> corps = Map.of(
                "model", modele,
                "background", true,
                "input", entrees(prompt, references),
                "response_format", Map.of("type", "image", "mime_type", "image/jpeg",
                        "aspect_ratio", RATIOS.contains(ratio) ? ratio : "1:1", "image_size", taille));
        long debut = System.currentTimeMillis();
        log.info("Image gemini / {} — envoi de la demande", modele);
        try {
            JsonNode rep = http.post().uri("/interactions")
                    .header("x-goog-api-key", cle)
                    .header("Api-Revision", REVISION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corps)
                    .retrieve()
                    .body(JsonNode.class);
            String id = rep == null ? "" : rep.path("id").asText("");
            while (rep != null && trouverImage(rep) == null && "in_progress".equals(rep.path("status").asText()) && !id.isEmpty()) {
                if (System.currentTimeMillis() - debut > delaiMaxMs) {
                    throw new GenerationException("L'image n'est pas prête après " + delaiMaxMs / 1000 + " s. Réessaie.");
                }
                pause();
                rep = http.get().uri("/interactions/{id}", id)
                        .header("x-goog-api-key", cle)
                        .header("Api-Revision", REVISION)
                        .retrieve()
                        .body(JsonNode.class);
            }
            String statut = rep == null ? "" : rep.path("status").asText("");
            if ("failed".equals(statut) || "cancelled".equals(statut)) {
                throw new GenerationException("Google n'a pas pu générer l'image (" + statut + ")"
                        + (rep.has("error") ? " : " + rep.path("error").path("message").asText(rep.path("error").toString()) : ""));
            }
            String base64 = trouverImage(rep);
            if (base64 == null) {
                throw new GenerationException("Le modèle n'a pas renvoyé d'image (prompt refusé ou réponse vide)");
            }
            journal.noter(ws, "gemini", modele, "image", null, null, System.currentTimeMillis() - debut, true, null);
            log.info("Image gemini / {} — reçue en {} s", modele, (System.currentTimeMillis() - debut) / 1000);
            return new ImageGeneree(Base64.getDecoder().decode(base64), modele);
        } catch (RestClientResponseException e) {
            String detail = e.getResponseBodyAsString();
            journal.noter(ws, "gemini", modele, "image", null, null, System.currentTimeMillis() - debut, false, detail);
            log.warn("Image gemini / {} — erreur {} : {}", modele, e.getStatusCode().value(), detail);
            String bas = detail.toLowerCase();
            if (bas.contains("billing") || bas.contains("free tier") || bas.contains("quota") || e.getStatusCode().value() == 429) {
                throw new GenerationException("La génération d'images est payante chez Google : active la facturation "
                        + "sur ta clé (Google AI Studio → Billing), ou réessaie plus tard si le quota est atteint.", e);
            }
            throw new GenerationException("Erreur du générateur d'images (" + e.getStatusCode().value() + ")", e);
        } catch (RuntimeException e) {
            log.warn("Image gemini / {} — échec : {}", modele, e.getMessage());
            if (e instanceof GenerationException g) {
                throw g;
            }
            journal.noter(ws, "gemini", modele, "image", null, null, System.currentTimeMillis() - debut, false, e.getMessage());
            throw new GenerationException("Le service d'images de Google ne répond pas. Vérifie ta connexion et réessaie.", e);
        }
    }

    /** Le texte, puis les images de référence (logo…) en base64. */
    private static List<Map<String, Object>> entrees(String prompt, List<Reference> references) {
        List<Map<String, Object>> liste = new java.util.ArrayList<>();
        liste.add(Map.of("type", "text", "text", prompt));
        if (references != null) {
            for (Reference r : references) {
                liste.add(Map.of("type", "image", "mime_type", r.mime(), "data", Base64.getEncoder().encodeToString(r.octets())));
            }
        }
        return liste;
    }

    private static void pause() {
        try {
            Thread.sleep(PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenerationException("Génération interrompue", e);
        }
    }

    /**
     * Cherche l'image dans la réponse : output_image.data, ou le dernier bloc {"type":"image","data":…}
     * (la forme exacte de la réponse REST n'est pas documentée, on parcourt donc l'arbre).
     */
    static String trouverImage(JsonNode n) {
        if (n == null) return null;
        JsonNode direct = n.path("output_image").path("data");
        if (direct.isTextual() && !direct.asText().isEmpty()) return direct.asText();
        String trouve = null;
        if (n.isObject()) {
            JsonNode data = n.get("data");
            boolean estImage = "image".equals(n.path("type").asText())
                    || n.path("mime_type").asText().startsWith("image/")
                    || n.path("mimeType").asText().startsWith("image/");
            if (estImage && data != null && data.isTextual() && data.asText().length() > 100) {
                trouve = data.asText();
            }
            Iterator<JsonNode> it = n.elements();
            while (it.hasNext()) {
                String sous = trouverImage(it.next());
                if (sous != null) trouve = sous;
            }
        } else if (n.isArray()) {
            for (JsonNode x : n) {
                String sous = trouverImage(x);
                if (sous != null) trouve = sous;
            }
        }
        return trouve;
    }
}
