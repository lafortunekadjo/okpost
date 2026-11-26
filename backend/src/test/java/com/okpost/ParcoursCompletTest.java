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

        // Tout valider : ce qui attend une réponse est validé d'un coup, une modification demandée n'est pas écrasée
        JsonNode lienTout = appeler(post("/api/v1/calendriers/" + calId + "/liens-validation"), jeton,
                Map.of("publications", ids.subList(3, 6)), 201);
        String urlTout = lienTout.get("url").asText();
        String jetonTout = urlTout.substring(urlTout.lastIndexOf('/') + 1);
        appeler(post("/api/v1/public/validation/" + jetonTout + "/publications/" + ids.get(4)), null,
                Map.of("decision", "A_MODIFIER", "commentaire", "Autre couleur"), 204);
        assertThat(appeler(post("/api/v1/public/validation/" + jetonTout + "/tout-valider"), null, null, 200).get("validees").asInt()).isEqualTo(2);
        JsonNode apresTout = appeler(get("/api/v1/calendriers/" + calId), jeton, null, 200);
        assertThat(statut(apresTout, ids.get(3))).isEqualTo("VALIDEE");
        assertThat(statut(apresTout, ids.get(4))).isEqualTo("A_MODIFIER");
        assertThat(statut(apresTout, ids.get(5))).isEqualTo("VALIDEE");
        assertThat(appeler(post("/api/v1/public/validation/" + jetonTout + "/tout-valider"), null, null, 200).get("validees").asInt()).isZero();
        // Un lien de partage (lecture seule) ne peut rien valider
        appeler(post("/api/v1/public/validation/" + jetonPartage + "/tout-valider"), null, null, 403);

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

    @Autowired com.okpost.abonnement.AbonnementRepository abonnementsDepot;

    /** Un compte d'un autre espace, invité sur UN client : il ne voit que lui, et perd tout dès que l'accès est retiré. */
    @Test
    void collaborationSurUnSeulClient() throws Exception {
        String suffixe = UUID.randomUUID().toString();
        JsonNode a = appeler(post("/api/v1/auth/inscription"), null,
                Map.of("nomEspace", "Agence A", "nom", "Alice", "email", "alice+" + suffixe + "@exemple.com", "motDePasse", "motdepasse-ok"), 200);
        String jetonA = a.get("jetonAcces").asText();
        UUID espaceA = UUID.fromString(a.get("workspaceId").asText());
        // L'essai n'autorise qu'un client : la formule PRO permet d'en avoir deux
        var abonnement = abonnementsDepot.findFirstByWorkspaceIdOrderByFinDesc(espaceA).orElseThrow();
        abonnement.setFormule(com.okpost.abonnement.Formule.PRO);
        abonnementsDepot.save(abonnement);
        String partage = appeler(post("/api/v1/clients"), jetonA, Map.of("nom", "Boutique partagée", "langue", "fr"), 201).get("id").asText();
        String confidentiel = appeler(post("/api/v1/clients"), jetonA, Map.of("nom", "Client confidentiel", "langue", "fr"), 201).get("id").asText();

        String emailB = "awa+" + suffixe + "@exemple.com";
        JsonNode b = appeler(post("/api/v1/auth/inscription"), null,
                Map.of("nomEspace", "Agence B", "nom", "Awa", "email", emailB, "motDePasse", "motdepasse-ok"), 200);
        String jetonB = b.get("jetonAcces").asText();

        // Sans invitation : aucune session limitée possible
        appeler(post("/api/v1/acces/contexte"), jetonB, Map.of("clientId", partage), 403);
        assertThat(appeler(get("/api/v1/acces/partages"), jetonB, null, 200)).isEmpty();

        // Invitation : adresse inconnue refusée, puis acceptée pour le compte existant ; pas deux fois
        appeler(post("/api/v1/clients/" + partage + "/collaborateurs"), jetonA, Map.of("email", "inconnu+" + suffixe + "@exemple.com"), 400);
        JsonNode collab = appeler(post("/api/v1/clients/" + partage + "/collaborateurs"), jetonA, Map.of("email", emailB), 201);
        appeler(post("/api/v1/clients/" + partage + "/collaborateurs"), jetonA, Map.of("email", emailB), 400);
        // Un invité ne peut inviter personne
        assertThat(appeler(get("/api/v1/acces/partages"), jetonB, null, 200)).hasSize(1);

        // Session limitée à ce client, dans l'espace du propriétaire
        JsonNode ctx = appeler(post("/api/v1/acces/contexte"), jetonB, Map.of("clientId", partage), 200);
        String jetonCtx = ctx.get("jetonAcces").asText();
        assertThat(ctx.get("role").asText()).isEqualTo("COLLAB");
        assertThat(ctx.get("workspaceId").asText()).isEqualTo(espaceA.toString());
        assertThat(ctx.get("clientId").asText()).isEqualTo(partage);

        // Il ne voit que ce client, même en connaissant l'identifiant de l'autre
        JsonNode liste = appeler(get("/api/v1/clients"), jetonCtx, null, 200);
        assertThat(liste).hasSize(1);
        assertThat(liste.get(0).get("id").asText()).isEqualTo(partage);
        appeler(get("/api/v1/clients/" + confidentiel), jetonCtx, null, 404);
        appeler(get("/api/v1/clients/" + confidentiel + "/calendriers"), jetonCtx, null, 404);
        appeler(post("/api/v1/clients/" + confidentiel + "/collaborateurs"), jetonCtx, Map.of("email", emailB), 403);

        // Il travaille sur le client partagé
        appeler(put("/api/v1/clients/" + partage), jetonCtx, Map.of("nom", "Boutique partagée", "langue", "fr", "slogan", "Modifié par l'invité"), 200);
        assertThat(appeler(get("/api/v1/clients/" + partage), jetonCtx, null, 200).get("slogan").asText()).isEqualTo("Modifié par l'invité");

        // Mais rien de ce qui concerne l'espace du propriétaire
        appeler(post("/api/v1/clients"), jetonCtx, Map.of("nom", "Nouveau", "langue", "fr"), 403);
        appeler(get("/api/v1/membres"), jetonCtx, null, 403);
        appeler(get("/api/v1/abonnements/courant"), jetonCtx, null, 403);
        appeler(get("/api/v1/clients/archives"), jetonCtx, null, 403);
        appeler(delete("/api/v1/clients/" + partage), jetonCtx, null, 403);
        appeler(delete("/api/v1/clients/" + partage + "/definitif"), jetonCtx, null, 403);
        appeler(get("/api/v1/clients/" + partage + "/collaborateurs"), jetonCtx, null, 403);

        // Le propriétaire voit son collaborateur, puis retire l'accès : le jeton encore valide ne donne plus rien
        assertThat(appeler(get("/api/v1/clients/" + partage + "/collaborateurs"), jetonA, null, 200)).hasSize(1);
        appeler(delete("/api/v1/clients/" + partage + "/collaborateurs/" + collab.get("id").asText()), jetonA, null, 204);
        appeler(get("/api/v1/clients/" + partage), jetonCtx, null, 404);
        assertThat(appeler(get("/api/v1/clients"), jetonCtx, null, 200)).isEmpty();
        appeler(post("/api/v1/acces/contexte"), jetonB, Map.of("clientId", partage), 403);
        assertThat(appeler(get("/api/v1/acces/partages"), jetonB, null, 200)).isEmpty();
    }

    @Autowired com.okpost.securite.UtilisateurRepository utilisateursDepot;
    @Autowired com.okpost.securite.WorkspaceRepository espacesDepot;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encodeurMotsDePasse;

    /** Le propriétaire de la plateforme suit les comptes, change leur formule et modifie les formules elles-mêmes. */
    @Test
    void administrationDesFormulesEtDesAbonnements() throws Exception {
        String suffixe = UUID.randomUUID().toString();
        // Un super-admin créé directement (l'amorçage au démarrage dépend de variables d'environnement)
        var espaceProprio = new com.okpost.securite.Workspace();
        espaceProprio.setNom("Plateforme " + suffixe);
        espacesDepot.save(espaceProprio);
        var sa = new com.okpost.securite.Utilisateur();
        sa.setWorkspaceId(espaceProprio.getId());
        sa.setNom("Propriétaire");
        sa.setEmail("root+" + suffixe + "@exemple.com");
        sa.setMotDePasseHash(encodeurMotsDePasse.encode("motdepasse-ok"));
        sa.setRole(com.okpost.securite.Role.SUPER_ADMIN);
        utilisateursDepot.save(sa);
        var aboSa = new com.okpost.abonnement.Abonnement();
        aboSa.setWorkspaceId(espaceProprio.getId());
        aboSa.setFormule(com.okpost.abonnement.Formule.AGENCE);
        aboSa.setDebut(LocalDate.now());
        aboSa.setFin(LocalDate.now().plusYears(50));
        aboSa.setStatut(com.okpost.abonnement.Abonnement.Statut.ACTIF);
        aboSa.setMode(com.okpost.abonnement.Abonnement.Mode.ESPECES);
        abonnementsDepot.save(aboSa);
        String jetonSa = appeler(post("/api/v1/auth/connexion"), null, Map.of("email", sa.getEmail(), "motDePasse", "motdepasse-ok"), 200)
                .get("jetonAcces").asText();

        // Un compte ordinaire, en essai : un seul client
        JsonNode b = appeler(post("/api/v1/auth/inscription"), null,
                Map.of("nomEspace", "Agence B " + suffixe, "nom", "Awa", "email", "awa+" + suffixe + "@exemple.com", "motDePasse", "motdepasse-ok"), 200);
        String jetonB = b.get("jetonAcces").asText();
        String espaceB = b.get("workspaceId").asText();
        appeler(post("/api/v1/clients"), jetonB, Map.of("nom", "Premier", "langue", "fr"), 201);
        appeler(post("/api/v1/clients"), jetonB, Map.of("nom", "Deuxième", "langue", "fr"), 402);

        // Les écrans d'administration sont fermés aux comptes ordinaires
        appeler(get("/api/v1/admin/formules"), jetonB, null, 403);
        appeler(get("/api/v1/admin/comptes"), jetonB, null, 403);
        appeler(put("/api/v1/admin/comptes/" + espaceB + "/abonnement"), jetonB,
                Map.of("formule", "PRO", "fin", LocalDate.now().plusDays(30).toString()), 403);
        appeler(put("/api/v1/admin/formules/PRO"), jetonB,
                Map.of("nom", "Pro", "prixMensuelXaf", 1, "maxClients", 99, "maxMembres", 9, "calendriersParMois", 9, "imagesParMois", 9), 403);

        // Suivi : formules et comptes
        assertThat(appeler(get("/api/v1/admin/formules"), jetonSa, null, 200)).hasSize(4);
        JsonNode vue = appeler(get("/api/v1/admin/comptes"), jetonSa, null, 200);
        JsonNode compteB = null;
        JsonNode comptePlateforme = null;
        for (JsonNode c : vue.get("comptes")) {
            if (c.get("id").asText().equals(espaceB)) compteB = c;
            if (c.get("id").asText().equals(espaceProprio.getId().toString())) comptePlateforme = c;
        }
        assertThat(compteB).isNotNull();
        assertThat(compteB.get("formule").asText()).isEqualTo("ESSAI");
        assertThat(compteB.get("statutAbonnement").asText()).isEqualTo("ESSAI");
        assertThat(compteB.get("clients").asInt()).isEqualTo(1);
        assertThat(compteB.get("maxClients").asInt()).isEqualTo(1);
        assertThat(compteB.get("procheLimite").asBoolean()).isTrue();
        assertThat(compteB.get("interne").asBoolean()).isFalse();
        assertThat(comptePlateforme.get("interne").asBoolean()).isTrue();
        assertThat(vue.get("totaux").get("essais").asInt()).isGreaterThanOrEqualTo(1);

        // Attribuer la formule SOLO pendant 30 jours : trois clients permis
        appeler(put("/api/v1/admin/comptes/" + espaceB + "/abonnement"), jetonSa,
                Map.of("formule", "SOLO", "fin", LocalDate.now().plusDays(30).toString()), 200);
        appeler(post("/api/v1/clients"), jetonB, Map.of("nom", "Deuxième", "langue", "fr"), 201);

        // Modifier la formule elle-même : 2 clients au plus et un autre prix, appliqués tout de suite au compte
        JsonNode solo = appeler(put("/api/v1/admin/formules/SOLO"), jetonSa,
                Map.of("nom", "Solo", "prixMensuelXaf", 9000, "maxClients", 2, "maxMembres", 1, "calendriersParMois", 3, "imagesParMois", 30), 200);
        assertThat(solo.get("maxClients").asInt()).isEqualTo(2);
        assertThat(solo.get("comptes").asInt()).isGreaterThanOrEqualTo(1);
        appeler(post("/api/v1/clients"), jetonB, Map.of("nom", "Troisième", "langue", "fr"), 402);
        JsonNode abo = appeler(get("/api/v1/abonnements/courant"), jetonB, null, 200);
        assertThat(abo.get("formule").asText()).isEqualTo("SOLO");
        assertThat(abo.get("maxClients").asInt()).isEqualTo(2);
        assertThat(abo.get("prixMensuelXaf").asLong()).isEqualTo(9000);

        // Valeurs absurdes et formule inconnue refusées
        appeler(put("/api/v1/admin/formules/SOLO"), jetonSa,
                Map.of("nom", "Solo", "prixMensuelXaf", -1, "maxClients", 2, "maxMembres", 1, "calendriersParMois", 3, "imagesParMois", 30), 400);
        appeler(put("/api/v1/admin/formules/INCONNUE"), jetonSa,
                Map.of("nom", "X", "prixMensuelXaf", 1, "maxClients", 2, "maxMembres", 1, "calendriersParMois", 3, "imagesParMois", 30), 404);

        // Les changements sont au journal d'administration
        assertThat(appeler(get("/api/v1/admin/journal?action=ABONNEMENT_MODIFIE"), jetonSa, null, 200).get("total").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(appeler(get("/api/v1/admin/journal?action=FORMULE_MODIFIEE"), jetonSa, null, 200).get("total").asLong()).isGreaterThanOrEqualTo(1);

        // Suspension : une date de fin dépassée de plus de 5 jours met le compte en lecture seule
        appeler(put("/api/v1/admin/comptes/" + espaceB + "/abonnement"), jetonSa,
                Map.of("formule", "SOLO", "fin", LocalDate.now().minusDays(10).toString()), 200);
        appeler(post("/api/v1/clients"), jetonB, Map.of("nom", "Quatrième", "langue", "fr"), 402);

        // La formule SOLO retrouve ses valeurs d'origine pour ne pas influencer les autres scénarios
        appeler(put("/api/v1/admin/formules/SOLO"), jetonSa,
                Map.of("nom", "Solo", "prixMensuelXaf", 7500, "maxClients", 3, "maxMembres", 1, "calendriersParMois", 3, "imagesParMois", 30), 200);
    }

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
