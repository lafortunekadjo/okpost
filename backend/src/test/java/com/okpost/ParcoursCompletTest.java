package com.okpost;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.okpost.ia.GenerateurIA;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "okpost.ia.fournisseur=factice",
        "okpost.ia.debit-par-minute=6000",
        "okpost.fichiers.dossier=target/test-fichiers",
        "okpost.jwt.secret=secret-de-test-secret-de-test-secret-de-test"
})
class ParcoursCompletTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class Config {
        @Bean
        GenerateurIA generateurFactice() {
            return new IaFactice();
        }

        @Bean
        com.okpost.ia.GenerateurImage imageFactice() {
            return new com.okpost.ia.GenerateurImage() {
                public String nom() { return "factice"; }
                public boolean configure() { return true; }
                public ImageGeneree generer(UUID ws, String prompt, String ratio, java.util.List<Reference> refs) {
                    return new ImageGeneree(PNG, "modele-test");
                }
            };
        }
    }

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.okpost.reseaux.MetaApi meta;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Test
    void parcoursDeLaFicheALaValidation() throws Exception {
        String jeton = inscrire("Agence Test", "vanessa+" + UUID.randomUUID() + "@exemple.com");

        // Lot 1 : fiche client
        Map<String, Object> fiche = Map.of(
                "nom", "Agro-Food FM", "secteur", "Agroalimentaire", "ville", "Douala", "pays", "Cameroun",
                "langue", "fr", "couleurs", List.of("#2E7D32"),
                "offres", List.of(Map.of("nom", "Formation transformation", "prix", "50 000 FCFA")));
        JsonNode client = appeler(post("/api/v1/clients"), jeton, fiche, 201);
        String clientId = client.get("id").asText();

        MockMultipartFile logo = new MockMultipartFile("fichier", "logo.png", "image/png", PNG);
        MvcResult upload = mvc.perform(multipart("/api/v1/clients/" + clientId + "/assets").file(logo)
                        .param("type", "LOGO_COULEUR").header("Authorization", "Bearer " + jeton))
                .andExpect(status().isCreated()).andReturn();
        String urlLogo = json.readTree(upload.getResponse().getContentAsString()).get("url").asText();
        mvc.perform(get(urlLogo)).andExpect(status().isOk());

        MockMultipartFile faux = new MockMultipartFile("fichier", "x.png", "image/png", "<html>".getBytes());
        mvc.perform(multipart("/api/v1/clients/" + clientId + "/assets").file(faux)
                .param("type", "PHOTO").header("Authorization", "Bearer " + jeton))
                .andExpect(status().isUnsupportedMediaType());

        // Lot 2 et 3 : calendrier généré en tâche de fond
        LocalDate debut = LocalDate.now().plusDays(1);
        JsonNode cal = appeler(post("/api/v1/clients/" + clientId + "/calendriers"), jeton, Map.of(
                "debut", debut.toString(), "fin", debut.plusDays(13).toString(),
                "rythme", Map.of("facebook", 3, "tiktok", 2), "objectifs", "Notoriété"), 202);
        String calId = cal.get("id").asText();

        JsonNode vue = null;
        for (int i = 0; i < 50; i++) {
            vue = appeler(get("/api/v1/calendriers/" + calId), jeton, null, 200);
            if ("TERMINE".equals(vue.get("statutGeneration").asText())) break;
            Thread.sleep(200);
        }
        assertThat(vue.get("statutGeneration").asText()).isEqualTo("TERMINE");
        assertThat(vue.get("total").asInt()).isEqualTo(vue.get("detaillees").asInt()).isGreaterThan(0);
        List<String> ids = new ArrayList<>();
        vue.get("publications").forEach(p -> {
            ids.add(p.get("id").asText());
            assertThat(p.get("statut").asText()).isEqualTo("BROUILLON");
            if ("TIKTOK".equals(p.get("reseau").asText())) {
                assertThat(p.get("format").asText()).isEqualTo("VIDEO");
                assertThat(p.get("scenario").get("plans").size()).isPositive();
            } else {
                assertThat(p.get("brief").get("promptImage").asText()).isNotBlank();
            }
        });

        // Modification et régénération d'un champ
        JsonNode pub = appeler(patch("/api/v1/publications/" + ids.get(0)), jeton,
                Map.of("legende", "Légende revue par le CM"), 200);
        assertThat(pub.get("legende").asText()).isEqualTo("Légende revue par le CM");
        JsonNode regen = appeler(post("/api/v1/publications/" + ids.get(0) + "/regenerer"), jeton,
                Map.of("champ", "legende", "consigne", "plus court"), 200);
        assertThat(regen.get("legende").asText()).contains("plus court");

        // Image générée à partir du prompt du brief
        String pubImage = null;
        for (JsonNode p : vue.get("publications")) {
            if (!"VIDEO".equals(p.get("format").asText())) { pubImage = p.get("id").asText(); break; }
        }
        JsonNode visuel = appeler(post("/api/v1/publications/" + pubImage + "/visuels"), jeton, Map.of(), 201);
        mvc.perform(get(visuel.get("url").asText())).andExpect(status().isOk());
        assertThat(appeler(get("/api/v1/publications/" + pubImage), jeton, null, 200).get("visuels").size()).isEqualTo(1);

        // Vidéo téléversée par le CM (en-tête MP4 « ftyp »)
        byte[] mp4 = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 0, 0};
        MockMultipartFile video = new MockMultipartFile("fichier", "tournage.mp4", "video/mp4", mp4);
        MvcResult envoi = mvc.perform(multipart("/api/v1/publications/" + pubImage + "/medias").file(video)
                        .header("Authorization", "Bearer " + jeton))
                .andExpect(status().isCreated()).andReturn();
        JsonNode media = json.readTree(envoi.getResponse().getContentAsString());
        assertThat(media.get("type").asText()).isEqualTo("VIDEO");
        assertThat(media.get("source").asText()).isEqualTo("TELEVERSE");
        mvc.perform(get(media.get("url").asText())).andExpect(status().isOk());

        // Lien de partage en lecture seule
        JsonNode partage = appeler(post("/api/v1/calendriers/" + calId + "/partage"), jeton, Map.of(), 201);
        String urlPartage = partage.get("url").asText();
        assertThat(urlPartage).contains("/c/");
        String jetonPartage = urlPartage.substring(urlPartage.lastIndexOf('/') + 1);
        JsonNode vuePartage = appeler(get("/api/v1/public/partage/" + jetonPartage), null, null, 200);
        assertThat(vuePartage.get("publications").size()).isEqualTo(vue.get("total").asInt());
        assertThat(vuePartage.toString()).doesNotContain("promptImage\":\"A photo");
        appeler(post("/api/v1/public/validation/" + jetonPartage + "/publications/" + ids.get(0)), null,
                Map.of("decision", "OK"), 403);

        // Lot 4 : lien de validation et portail public
        JsonNode lien = appeler(post("/api/v1/calendriers/" + calId + "/liens-validation"), jeton,
                Map.of("publications", ids.subList(0, 3)), 201);
        String url = lien.get("url").asText();
        String jetonLien = url.substring(url.lastIndexOf('/') + 1);
        assertThat(jetonLien).hasSizeGreaterThanOrEqualTo(43);

        JsonNode portail = appeler(get("/api/v1/public/validation/" + jetonLien), null, null, 200);
        assertThat(portail.get("clientNom").asText()).isEqualTo("Agro-Food FM");
        assertThat(portail.get("total").asInt()).isEqualTo(3);
        assertThat(portail.get("couleur").asText()).isEqualTo("#2E7D32");

        appeler(post("/api/v1/public/validation/" + jetonLien + "/publications/" + ids.get(0)), null,
                Map.of("decision", "OK"), 204);
        appeler(post("/api/v1/public/validation/" + jetonLien + "/publications/" + ids.get(1)), null,
                Map.of("decision", "A_MODIFIER"), 400);
        appeler(post("/api/v1/public/validation/" + jetonLien + "/publications/" + ids.get(1)), null,
                Map.of("decision", "A_MODIFIER", "commentaire", "Changer la photo"), 204);
        appeler(put("/api/v1/public/validation/" + jetonLien + "/publications/" + ids.get(2) + "/legende"), null,
                Map.of("legende", "Texte du client"), 204);
        // Une publication hors du lien est refusée
        appeler(post("/api/v1/public/validation/" + jetonLien + "/publications/" + ids.get(3)), null,
                Map.of("decision", "OK"), 404);

        JsonNode apres = appeler(get("/api/v1/calendriers/" + calId), jeton, null, 200);
        assertThat(statut(apres, ids.get(0))).isEqualTo("VALIDEE");
        assertThat(statut(apres, ids.get(1))).isEqualTo("A_MODIFIER");
        assertThat(statut(apres, ids.get(2))).isEqualTo("VALIDEE");

        // Le CM corrige : la publication repasse en brouillon
        JsonNode corrigee = appeler(patch("/api/v1/publications/" + ids.get(1)), jeton,
                Map.of("legende", "Corrigée"), 200);
        assertThat(corrigee.get("statut").asText()).isEqualTo("BROUILLON");
        assertThat(corrigee.get("retours").size()).isEqualTo(1);

        // Lien révoqué : le portail n'est plus accessible
        appeler(delete("/api/v1/liens-validation/" + lien.get("id").asText()), jeton, null, 204);
        appeler(get("/api/v1/public/validation/" + jetonLien), null, null, 410);

        // Isolation : une autre agence ne voit rien
        String autre = inscrire("Autre agence", "autre+" + UUID.randomUUID() + "@exemple.com");
        appeler(get("/api/v1/clients/" + clientId), autre, null, 404);
        appeler(get("/api/v1/calendriers/" + calId), autre, null, 404);
        appeler(patch("/api/v1/publications/" + ids.get(0)), autre, Map.of("legende", "pirate"), 404);
        assertThat(appeler(get("/api/v1/clients"), autre, null, 200).size()).isZero();

        // Formule d'essai : un seul client
        appeler(post("/api/v1/clients"), jeton, Map.of("nom", "Deuxième"), 402);
    }

    @Test
    void publicationAutomatiqueSurFacebookApresValidation() throws Exception {
        org.mockito.Mockito.when(meta.prolonger(org.mockito.ArgumentMatchers.anyString())).thenAnswer(i -> i.getArgument(0));
        org.mockito.Mockito.when(meta.pages("jeton-utilisateur"))
                .thenReturn(List.of(new com.okpost.reseaux.MetaApi.Page("123", "Page My2-0", "jeton-page")));
        org.mockito.Mockito.when(meta.publierTexte(org.mockito.ArgumentMatchers.eq("123"), org.mockito.ArgumentMatchers.eq("jeton-page"),
                        org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new com.okpost.reseaux.MetaApi.Resultat("123_456", "https://www.facebook.com/123_456"));

        String jeton = inscrire("Agence FB", "fb+" + UUID.randomUUID() + "@exemple.com");
        JsonNode client = appeler(post("/api/v1/clients"), jeton, Map.of("nom", "My2-0", "autoPublication", true,
                "heurePublication", "18:00"), 201);
        String clientId = client.get("id").asText();
        assertThat(client.get("autoPublication").asBoolean()).isTrue();

        // Liaison de la Page avec un jeton collé : une seule Page, elle devient active
        JsonNode comptes = appeler(post("/api/v1/clients/" + clientId + "/comptes-sociaux/facebook/jeton"), jeton,
                Map.of("jeton", "jeton-utilisateur"), 200);
        assertThat(comptes.get(0).get("actif").asBoolean()).isTrue();
        assertThat(comptes.toString()).doesNotContain("jeton-page");

        LocalDate debut = LocalDate.now().plusDays(1);
        String calId = appeler(post("/api/v1/clients/" + clientId + "/calendriers"), jeton, Map.of(
                "debut", debut.toString(), "fin", debut.plusDays(6).toString(), "rythme", Map.of("facebook", 2)), 202)
                .get("id").asText();
        JsonNode vue = null;
        for (int i = 0; i < 50; i++) {
            vue = appeler(get("/api/v1/calendriers/" + calId), jeton, null, 200);
            if ("TERMINE".equals(vue.get("statutGeneration").asText())) break;
            Thread.sleep(200);
        }
        String pubId = vue.get("publications").get(0).get("id").asText();
        String url = appeler(post("/api/v1/calendriers/" + calId + "/liens-validation"), jeton,
                Map.of("publications", List.of(pubId)), 201).get("url").asText();
        String jetonLien = url.substring(url.lastIndexOf('/') + 1);

        // Le client valide : la publication est programmée automatiquement
        appeler(post("/api/v1/public/validation/" + jetonLien + "/publications/" + pubId), null, Map.of("decision", "OK"), 204);
        JsonNode pub = appeler(get("/api/v1/publications/" + pubId), jeton, null, 200);
        assertThat(pub.get("statut").asText()).isEqualTo("PROGRAMMEE");
        assertThat(pub.get("publicationPrevue").asText()).isNotBlank();

        // Publication immédiate
        JsonNode publiee = appeler(post("/api/v1/publications/" + pubId + "/publier-maintenant"), jeton, null, 200);
        assertThat(publiee.get("statut").asText()).isEqualTo("PUBLIEE");
        assertThat(publiee.get("lienPost").asText()).contains("123_456");
    }

    @Test
    void connexionEtBlocage() throws Exception {
        String email = "bloc+" + UUID.randomUUID() + "@exemple.com";
        inscrire("Agence", email);
        appeler(post("/api/v1/auth/connexion"), null, Map.of("email", email, "motDePasse", "motdepasse-ok"), 200);
        for (int i = 0; i < 5; i++) {
            appeler(post("/api/v1/auth/connexion"), null, Map.of("email", email, "motDePasse", "mauvais"), 401);
        }
        appeler(post("/api/v1/auth/connexion"), null, Map.of("email", email, "motDePasse", "motdepasse-ok"), 429);
        appeler(get("/api/v1/clients"), null, null, 401);
    }

    // ------------------------------------------------------------------

    private String inscrire(String espace, String email) throws Exception {
        JsonNode r = appeler(post("/api/v1/auth/inscription"), null,
                Map.of("nomEspace", espace, "nom", "Vanessa", "email", email, "motDePasse", "motdepasse-ok"), 200);
        return r.get("jetonAcces").asText();
    }

    private JsonNode appeler(MockHttpServletRequestBuilder req, String jeton, Object corps, int attendu) throws Exception {
        if (jeton != null) req.header("Authorization", "Bearer " + jeton);
        if (corps != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corps));
        MvcResult r = mvc.perform(req).andReturn();
        String texte = r.getResponse().getContentAsString();
        assertThat(r.getResponse().getStatus()).as(texte).isEqualTo(attendu);
        return texte.isBlank() ? null : json.readTree(texte);
    }

    private static String statut(JsonNode cal, String id) {
        for (JsonNode p : cal.get("publications")) {
            if (p.get("id").asText().equals(id)) return p.get("statut").asText();
        }
        return null;
    }
}
