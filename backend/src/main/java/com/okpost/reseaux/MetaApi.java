package com.okpost.reseaux;

import com.fasterxml.jackson.databind.JsonNode;
import com.okpost.config.OkpostProperties;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Appels à l'API Graph de Meta : connexion d'une Page (OAuth), liste des Pages, publication texte, photo ou vidéo.
 * Permissions : pages_show_list, pages_read_engagement, pages_manage_posts.
 */
@Component
public class MetaApi {

    public static final String PERMISSIONS = "pages_show_list,pages_read_engagement,pages_manage_posts,read_insights,pages_read_user_content";

    public record Page(String id, String nom, String jeton) {}

    public record Resultat(String postId, String lien) {}

    /** Publication lue sur la Page, avec ses chiffres. vues = null si la permission read_insights manque. */
    public record PostPage(String id, String message, java.time.Instant date, String lien, String type,
                           int reactions, int commentaires, int partages, Integer vues) {
        public int engagement() { return reactions + commentaires + partages; }
    }

    /** Résultat de lecture : posts, et si les vues ont pu être lues. */
    public record PostsPage(List<PostPage> posts, boolean vuesDisponibles, boolean commentairesDisponibles,
                            String remarque) {}

    public static class MetaException extends RuntimeException {
        public MetaException(String message) { super(message); }
    }

    private final OkpostProperties.Meta config;
    private final RestClient graph;
    private final RestClient graphVideo;

    public MetaApi(OkpostProperties props) {
        this.config = props.meta();
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(15));
        f.setReadTimeout(Duration.ofMinutes(10)); // envoi de vidéos
        String v = version();
        this.graph = RestClient.builder().requestFactory(f).baseUrl("https://graph.facebook.com/" + v).build();
        this.graphVideo = RestClient.builder().requestFactory(f).baseUrl("https://graph-video.facebook.com/" + v).build();
    }

    public boolean oauthConfigure() {
        return config != null && notBlank(config.appId()) && notBlank(config.appSecret());
    }

    /** Adresse de la fenêtre « Se connecter avec Facebook ». */
    public String urlConnexion(String etat, String redirection) {
        return UriComponentsBuilder.fromUriString("https://www.facebook.com/" + version() + "/dialog/oauth")
                .queryParam("client_id", config.appId())
                .queryParam("redirect_uri", redirection)
                .queryParam("state", etat)
                .queryParam("scope", PERMISSIONS)
                .queryParam("response_type", "code")
                .encode().build().toUriString();
    }

    /** Code OAuth → jeton utilisateur longue durée. */
    public String jetonDepuisCode(String code, String redirection) {
        JsonNode r = get("/oauth/access_token?client_id={a}&redirect_uri={r}&client_secret={s}&code={c}",
                config.appId(), redirection, config.appSecret(), code);
        return prolonger(r.path("access_token").asText());
    }

    /** Jeton utilisateur court → jeton longue durée (environ 60 jours). Les jetons de Page qui en dérivent n'expirent pas. */
    public String prolonger(String jetonUtilisateur) {
        if (!oauthConfigure()) return jetonUtilisateur;
        JsonNode r = get("/oauth/access_token?grant_type=fb_exchange_token&client_id={a}&client_secret={s}&fb_exchange_token={t}",
                config.appId(), config.appSecret(), jetonUtilisateur);
        return r.path("access_token").asText(jetonUtilisateur);
    }

    /** Pages que l'utilisateur administre, avec leur jeton de Page. */
    public List<Page> pages(String jetonUtilisateur) {
        JsonNode r = get("/me/accounts?fields=id,name,access_token&limit=100&access_token={t}", jetonUtilisateur);
        List<Page> pages = new ArrayList<>();
        r.path("data").forEach(p -> pages.add(new Page(p.path("id").asText(), p.path("name").asText(), p.path("access_token").asText())));
        StringBuilder diag = new StringBuilder();
        if (pages.isEmpty()) pages.addAll(pagesAutorisees(jetonUtilisateur, diag));
        if (pages.isEmpty()) {
            // Peut-être un jeton de Page collé directement : « me » désigne alors la Page.
            // On vérifie que c'est bien une Page (elle a une catégorie) et pas le profil de la personne.
            JsonNode me;
            try {
                me = get("/me?fields=id,name,category&access_token={t}", jetonUtilisateur);
            } catch (MetaException e) {
                me = get("/me?fields=id,name&access_token={t}", jetonUtilisateur);
            }
            if (me.has("category")) {
                pages.add(new Page(me.path("id").asText(), me.path("name").asText(), jetonUtilisateur));
            } else {
                throw new MetaException("Ce jeton donne accès au profil de " + me.path("name").asText("la personne")
                        + ", mais à aucune Page. Regénère-le avec pages_show_list, pages_read_engagement et "
                        + "pages_manage_posts, et coche la Page dans la fenêtre d'autorisation."
                        + (diag.length() > 0 ? " (Détail : " + diag + ")" : ""));
            }
        }
        return pages;
    }

    /**
     * Quand /me/accounts revient vide (Pages gérées via un portefeuille Business, par exemple), on lit les Pages
     * cochées dans la fenêtre d'autorisation (« étendues granulaires » de debug_token) et on demande leur jeton.
     */
    private List<Page> pagesAutorisees(String jetonUtilisateur, StringBuilder diag) {
        List<Page> pages = new ArrayList<>();
        if (!oauthConfigure()) { diag.append("META_APP_ID / META_APP_SECRET absents, Pages cochées non lues"); return pages; }
        try {
            JsonNode d = get("/debug_token?input_token={t}&access_token={a}", jetonUtilisateur,
                    config.appId() + "|" + config.appSecret()).path("data");
            java.util.Set<String> ids = new java.util.LinkedHashSet<>();
            d.path("granular_scopes").forEach(s -> {
                if ("pages_manage_posts".equals(s.path("scope").asText()))
                    s.path("target_ids").forEach(id -> ids.add(id.asText()));
            });
            if (ids.isEmpty()) diag.append("aucune Page cochée trouvée pour pages_manage_posts");
            for (String id : ids) {
                try {
                    JsonNode p = get("/" + id + "?fields=id,name,access_token&access_token={t}", jetonUtilisateur);
                    if (!p.path("access_token").asText().isBlank())
                        pages.add(new Page(p.path("id").asText(), p.path("name").asText(), p.path("access_token").asText()));
                    else diag.append("Page ").append(id).append(" : Facebook ne donne pas son jeton ; ");
                } catch (MetaException e) {
                    diag.append("Page ").append(id).append(" : ").append(e.getMessage()).append(" ; ");
                }
            }
        } catch (MetaException e) {
            diag.append("lecture des Pages cochées : ").append(e.getMessage());
        }
        return pages;
    }

    public Resultat publierTexte(String pageId, String jeton, String message) {
        MultiValueMap<String, Object> f = new LinkedMultiValueMap<>();
        f.add("message", message);
        f.add("access_token", jeton);
        JsonNode r = post(graph, "/" + pageId + "/feed", f);
        String id = r.path("id").asText();
        return new Resultat(id, "https://www.facebook.com/" + id);
    }

    public Resultat publierPhoto(String pageId, String jeton, Resource image, String message) {
        MultiValueMap<String, Object> f = new LinkedMultiValueMap<>();
        f.add("source", image);
        f.add("message", message);
        f.add("access_token", jeton);
        JsonNode r = post(graph, "/" + pageId + "/photos", f);
        String id = r.path("post_id").asText(r.path("id").asText());
        return new Resultat(id, "https://www.facebook.com/" + id);
    }

    /**
     * Plusieurs photos dans une même publication : chaque photo est envoyée sans être publiée,
     * puis une publication les réunit (attached_media). Facebook les affiche en mosaïque.
     */
    public Resultat publierPhotos(String pageId, String jeton, List<Resource> images, String message) {
        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        for (int i = 0; i < images.size(); i++) {
            MultiValueMap<String, Object> photo = new LinkedMultiValueMap<>();
            photo.add("source", images.get(i));
            photo.add("published", "false");
            photo.add("access_token", jeton);
            String id = post(graph, "/" + pageId + "/photos", photo).path("id").asText();
            if (id.isEmpty()) throw new MetaException("Facebook : envoi de la photo " + (i + 1) + " refusé");
            f.add("attached_media[" + i + "]", "{\"media_fbid\":\"" + id + "\"}");
        }
        f.add("message", message);
        f.add("access_token", jeton);
        JsonNode r;
        try {
            r = graph.post().uri("/" + pageId + "/feed").contentType(MediaType.APPLICATION_FORM_URLENCODED).body(f)
                    .retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new MetaException(message(e));
        }
        String id = r.path("id").asText();
        return new Resultat(id, "https://www.facebook.com/" + id);
    }

    /**
     * Vidéo sur la Page. Essaie d'abord l'envoi classique (/videos) ; si Facebook le refuse
     * (« No permission to publish the video »), repasse par l'API Reels, que Meta privilégie désormais.
     */
    public Resultat publierVideo(String pageId, String jeton, Resource video, String description) {
        MultiValueMap<String, Object> f = new LinkedMultiValueMap<>();
        f.add("source", video);
        f.add("description", description);
        f.add("access_token", jeton);
        try {
            JsonNode r = post(graphVideo, "/" + pageId + "/videos", f);
            String id = r.path("id").asText();
            return new Resultat(id, "https://www.facebook.com/" + pageId + "/videos/" + id);
        } catch (MetaException e) {
            if (!e.getMessage().contains("(#100)") && !e.getMessage().toLowerCase().contains("permission")) throw e;
            try {
                return publierReel(pageId, jeton, video, description);
            } catch (MetaException reel) {
                throw new MetaException(e.getMessage() + " — essai en Reel : " + reel.getMessage());
            }
        }
    }

    /** API Reels : démarrage, envoi du fichier, puis publication. */
    public Resultat publierReel(String pageId, String jeton, Resource video, String description) {
        JsonNode debut = postParams("/" + pageId + "/video_reels", "upload_phase", "start", "access_token", jeton);
        String videoId = debut.path("video_id").asText();
        String urlEnvoi = debut.path("upload_url").asText();
        if (videoId.isEmpty() || urlEnvoi.isEmpty()) throw new MetaException("Facebook : démarrage du Reel refusé");
        long taille;
        try {
            taille = video.contentLength();
        } catch (java.io.IOException e) {
            throw new MetaException("Vidéo illisible sur le serveur");
        }
        try {
            RestClient.create().post().uri(java.net.URI.create(urlEnvoi))
                    .header("Authorization", "OAuth " + jeton)
                    .header("offset", "0")
                    .header("file_size", String.valueOf(taille))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(video)
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new MetaException(message(e));
        }
        postParams("/" + pageId + "/video_reels", "upload_phase", "finish", "video_id", videoId,
                "video_state", "PUBLISHED", "description", description, "access_token", jeton);
        return new Resultat(videoId, "https://www.facebook.com/reel/" + videoId);
    }

    private static final String CHAMPS_POSTS = "id,message,created_time,permalink_url,status_type,"
            + "attachments{media_type},reactions.summary(total_count).limit(0),shares";

    /**
     * Publications de la Page sur une période (500 au plus), avec réactions, commentaires, partages et,
     * si l'application a la permission read_insights, les vues (métrique post_media_view, qui remplace les impressions).
     */
    public PostsPage posts(String pageId, String jeton, java.time.Instant depuis, java.time.Instant jusqua) {
        // Vues : permission read_insights. Commentaires : permission pages_read_user_content.
        // On essaie avec les deux, puis on retire ce que Facebook refuse, pour que le bilan s'affiche quand même.
        List<String> refus = new ArrayList<>();
        boolean[][] essais = {{true, true}, {false, true}, {true, false}, {false, false}};
        MetaException derniere = null;
        for (String arete : List.of("posts", "published_posts"))
        for (boolean[] e : essais) {
            try {
                List<PostPage> posts = lirePosts(arete, pageId, jeton, depuis, jusqua, e[0], e[1]);
                String remarque = null;
                if (!e[0] || !e[1]) {
                    List<String> manque = new ArrayList<>();
                    if (!e[0]) manque.add("vues (permission read_insights)");
                    if (!e[1]) manque.add("commentaires (permission pages_read_user_content)");
                    remarque = "Indisponibles avec ce jeton : " + String.join(", ", manque)
                            + ". Régénère le jeton de la Page avec ces permissions. Détail : " + String.join(" / ", refus);
                }
                return new PostsPage(posts, e[0], e[1], remarque);
            } catch (MetaException ex) {
                derniere = ex;
                if (!refus.contains(ex.getMessage())) refus.add(ex.getMessage());
            }
        }
        throw new MetaException(derniere.getMessage() + " — ajoute la permission pages_read_user_content au jeton de la Page"
                + " (cas d'utilisation « Gérer tout sur votre Page »), puis relie de nouveau la Page dans OkPost.");
    }

    private List<PostPage> lirePosts(String arete, String pageId, String jeton, java.time.Instant depuis, java.time.Instant jusqua,
                                     boolean avecVues, boolean avecCommentaires) {
        String champs = CHAMPS_POSTS + (avecCommentaires ? ",comments.summary(total_count).limit(0)" : "")
                + (avecVues ? ",insights.metric(post_media_view){values}" : "");
        JsonNode r = get("/" + pageId + "/" + arete + "?fields={f}&since={s}&until={u}&limit=100&access_token={t}",
                champs, depuis.getEpochSecond(), jusqua.getEpochSecond(), jeton);
        List<PostPage> posts = new ArrayList<>();
        for (int page = 0; page < 5 && r != null; page++) {
            for (JsonNode p : r.path("data")) posts.add(lirePost(p, avecVues));
            String suivant = r.path("paging").path("next").asText("");
            if (suivant.isEmpty()) break;
            try {
                r = graph.get().uri(java.net.URI.create(suivant)).retrieve().body(JsonNode.class);
            } catch (RestClientResponseException e) {
                throw new MetaException(message(e));
            }
        }
        return posts;
    }

    private static PostPage lirePost(JsonNode p, boolean avecVues) {
        String media = p.path("attachments").path("data").path(0).path("media_type").asText("");
        String statut = p.path("status_type").asText("");
        String type = switch (media) {
            case "photo" -> "IMAGE";
            case "video" -> "VIDEO";
            case "album" -> "ALBUM";
            case "link" -> "LIEN";
            default -> statut.contains("video") ? "VIDEO" : statut.contains("photo") ? "IMAGE"
                    : statut.contains("shared") ? "LIEN" : "TEXTE";
        };
        Integer vues = null;
        if (avecVues) {
            JsonNode v = p.path("insights").path("data").path(0).path("values").path(0).path("value");
            if (v.isNumber()) vues = v.asInt();
        }
        java.time.Instant date;
        try {
            date = java.time.OffsetDateTime.parse(p.path("created_time").asText(),
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")).toInstant();
        } catch (RuntimeException e) {
            date = null;
        }
        return new PostPage(p.path("id").asText(), p.path("message").asText(""), date,
                p.path("permalink_url").asText(""), type,
                p.path("reactions").path("summary").path("total_count").asInt(0),
                p.path("comments").path("summary").path("total_count").asInt(0),
                p.path("shares").path("count").asInt(0), vues);
    }

    /** Nombre d'abonnés de la Page (null si illisible). */
    public Integer abonnes(String pageId, String jeton) {
        try {
            JsonNode r = get("/" + pageId + "?fields=followers_count&access_token={t}", jeton);
            return r.has("followers_count") ? r.path("followers_count").asInt() : null;
        } catch (MetaException e) {
            return null;
        }
    }

    private JsonNode postParams(String chemin, String... cleValeur) {
        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        for (int i = 0; i + 1 < cleValeur.length; i += 2) f.add(cleValeur[i], cleValeur[i + 1]);
        try {
            return graph.post().uri(chemin).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(f)
                    .retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new MetaException(message(e));
        }
    }

    // ------------------------------------------------------------------

    private JsonNode get(String uri, Object... variables) {
        try {
            return graph.get().uri(uri, variables).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new MetaException(message(e));
        }
    }

    private JsonNode post(RestClient client, String uri, MultiValueMap<String, Object> formulaire) {
        try {
            return client.post().uri(uri).contentType(MediaType.MULTIPART_FORM_DATA).body(formulaire).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new MetaException(message(e));
        }
    }

    private static String message(RestClientResponseException e) {
        String corps = e.getResponseBodyAsString();
        int i = corps.indexOf("\"message\":\"");
        if (i >= 0) {
            int fin = corps.indexOf('"', i + 11);
            if (fin > i) return "Facebook : " + corps.substring(i + 11, fin);
        }
        return "Facebook : erreur " + e.getStatusCode().value();
    }

    private String version() {
        return config == null || !notBlank(config.version()) ? "v23.0" : config.version().trim();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
