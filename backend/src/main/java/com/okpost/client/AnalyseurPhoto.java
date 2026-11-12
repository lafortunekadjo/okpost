package com.okpost.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.okpost.config.OkpostProperties;
import com.okpost.ia.IaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Décrit une photo de la banque avec un modèle de vision : une phrase et des mots-clés français et anglais,
 * pour que BanqueImages retrouve la bonne photo selon le sujet d'une publication.
 * Sans clé Gemini, ou en cas d'échec, la photo reste utilisable : le CM peut saisir description et mots-clés.
 */
@Component
public class AnalyseurPhoto {

    private static final Logger log = LoggerFactory.getLogger(AnalyseurPhoto.class);

    /** Mot-clé réservé : la photo est écartée de la sélection automatique (floue, filigrane, texte envahissant…). */
    public static final String INUTILISABLE = "inutilisable";

    private static final int COTE_MAX = 1024;
    private static final int TAGS_MAX = 20;

    private static final String CONSIGNE = """
            Tu aides un community manager à classer les photos d'un client dans une banque d'images.
            Regarde la photo et réponds uniquement avec un JSON de la forme :
            {"description": "...", "tags": ["..."], "utilisable": true}
            - description : une phrase en français (25 mots maximum) qui dit ce que montre la photo : qui, quoi, où, ambiance.
            - tags : 10 à 16 mots-clés courts, la moitié en français et la moitié en anglais (mêmes idées dans les deux langues) :
              sujet, action, lieu, objets, nombre de personnes (ex. « groupe », « group »), émotion, moment de la journée, couleurs dominantes.
            - utilisable : false si la photo est floue, très sombre, porte un filigrane ou beaucoup de texte, ou n'est pas exploitable en affiche.
            N'invente rien : ne décris que ce qui est visible.""";

    private final OkpostProperties props;
    private final ClientAssetRepository assets;
    private final StockageFichiers stockage;
    private final ObjectMapper json;
    private final RestClient http;
    private final String cle;
    private final String modele;
    private final String url;

    public AnalyseurPhoto(OkpostProperties props, ClientAssetRepository assets, StockageFichiers stockage, ObjectMapper json) {
        this.props = props;
        this.assets = assets;
        this.stockage = stockage;
        this.json = json;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(15));
        f.setReadTimeout(Duration.ofSeconds(60));
        this.http = RestClient.builder().requestFactory(f).build();
        OkpostProperties.Gemini g = props.ia().gemini();
        this.cle = IaConfig.propre(g.apiKey(), "");
        this.modele = IaConfig.propre(g.modele(), "gemini-2.5-flash-lite");
        this.url = IaConfig.propre(g.baseUrl(), "https://generativelanguage.googleapis.com/v1beta/openai") + "/chat/completions";
    }

    public boolean configure() {
        return !cle.isEmpty();
    }

    /** Lance la description en arrière-plan. L'état de la photo passe à EN_COURS puis OK ou ECHEC. */
    @Async
    public void analyser(UUID assetId) {
        ClientAsset a = assets.findById(assetId).orElse(null);
        if (a == null || a.getType() != ClientAsset.Type.PHOTO || a.getMime().contains("svg")) return;
        if (!configure()) {
            a.setAnalyse(ClientAsset.Analyse.ECHEC);
            assets.save(a);
            log.info("Photo {} non décrite : pas de clé Gemini", assetId);
            return;
        }
        a.setAnalyse(ClientAsset.Analyse.EN_COURS);
        assets.save(a);
        try {
            byte[] petite = reduire(lireOctets(a));
            Resultat r = parser(json, appeler(petite));
            if (a.getDescription() == null || a.getDescription().isBlank()) a.setDescription(r.description());
            List<String> tags = new ArrayList<>(r.tags());
            // Les mots-clés saisis à la main sont conservés
            a.getTags().stream().filter(t -> !tags.contains(t)).forEach(tags::add);
            if (!r.utilisable() && !tags.contains(INUTILISABLE)) tags.add(INUTILISABLE);
            if (r.utilisable()) tags.remove(INUTILISABLE);
            a.setTags(nettoyerTags(tags));
            a.setAnalyse(ClientAsset.Analyse.OK);
        } catch (Exception e) {
            log.warn("Description de la photo {} impossible : {}", assetId, e.getMessage());
            a.setAnalyse(ClientAsset.Analyse.ECHEC);
        }
        assets.save(a);
    }

    private byte[] lireOctets(ClientAsset a) throws IOException {
        try (var in = stockage.lire(a.getChemin()).getInputStream()) {
            return in.readAllBytes();
        }
    }

    private String appeler(byte[] jpeg) {
        String donnees = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg);
        Map<String, Object> corps = Map.of(
                "model", modele,
                "temperature", 0.2,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(Map.of("role", "user", "content", List.of(
                        Map.of("type", "text", "text", CONSIGNE),
                        Map.of("type", "image_url", "image_url", Map.of("url", donnees))))));
        JsonNode rep = http.post().uri(url).header("Authorization", "Bearer " + cle)
                .body(corps).retrieve().body(JsonNode.class);
        String texte = rep == null ? "" : rep.path("choices").path(0).path("message").path("content").asText("");
        if (texte.isBlank()) throw new IllegalStateException("réponse vide");
        return texte;
    }

    // ------------------------------------------------------------------ outils (testables sans réseau)

    record Resultat(String description, List<String> tags, boolean utilisable) {}

    static Resultat parser(ObjectMapper json, String texte) throws IOException {
        String brut = texte.trim().replaceAll("^```(?:json)?\\s*|\\s*```$", "");
        JsonNode n = json.readTree(brut);
        String description = n.path("description").asText("").trim();
        List<String> tags = new ArrayList<>();
        n.path("tags").forEach(t -> tags.add(t.asText("")));
        if (description.isEmpty() && tags.isEmpty()) throw new IOException("ni description ni mots-clés");
        boolean utilisable = !n.has("utilisable") || n.path("utilisable").asBoolean(true);
        return new Resultat(description.length() > 600 ? description.substring(0, 600) : description,
                nettoyerTags(tags), utilisable);
    }

    /** Minuscules, sans doublon ni mot vide, 40 caractères maximum, 20 mots-clés au plus. */
    public static List<String> nettoyerTags(List<String> tags) {
        Set<String> uniques = new LinkedHashSet<>();
        for (String t : tags == null ? List.<String>of() : tags) {
            if (t == null) continue;
            String v = t.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (v.isEmpty()) continue;
            uniques.add(v.length() > 40 ? v.substring(0, 40).trim() : v);
            if (uniques.size() >= TAGS_MAX) break;
        }
        return new ArrayList<>(uniques);
    }

    /** Réduit la photo (côté maximum 1024 px) en JPEG : moins de volume envoyé, même lisibilité pour le modèle. */
    static byte[] reduire(byte[] octets) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(octets));
        if (src == null) throw new IOException("image illisible");
        double e = Math.min(1.0, (double) COTE_MAX / Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * e));
        int h = Math.max(1, (int) Math.round(src.getHeight() * e));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, w, h, java.awt.Color.WHITE, null);
        g.dispose();
        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            ImageIO.write(out, "jpg", sortie);
            return sortie.toByteArray();
        }
    }

    /** Mots significatifs d'un texte : sans accents ni ponctuation, au singulier approximatif, sans mots vides. */
    public static Set<String> mots(String texte) {
        Set<String> m = new LinkedHashSet<>();
        if (texte == null) return m;
        String s = Normalizer.normalize(texte.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        for (String mot : s.split("[^a-z0-9]+")) {
            if (mot.length() < 3 || VIDES.contains(mot)) continue;
            m.add(mot.length() > 4 && mot.endsWith("s") ? mot.substring(0, mot.length() - 1) : mot);
        }
        return m;
    }

    private static final Set<String> VIDES = Set.of(
            "the", "and", "with", "for", "from", "into", "that", "this", "your", "are", "was", "has", "his", "her", "its",
            "les", "des", "une", "dans", "pour", "avec", "sur", "est", "pas", "que", "qui", "ton", "ses", "aux", "par",
            "mon", "mes", "tes", "nos", "vos", "leur", "ils", "elle", "elles", "nous", "vous", "tout", "plus", "mais",
            "photo", "image", "photorealistic", "documentary", "natural", "candid", "shot", "text", "letters", "logo",
            "watermark", "sharp", "focus", "realistic", "authentic", "expressions", "background", "scene");
}
