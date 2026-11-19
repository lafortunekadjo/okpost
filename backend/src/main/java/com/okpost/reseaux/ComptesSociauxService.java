package com.okpost.reseaux;

import com.okpost.client.Client;
import com.okpost.client.ClientRepository;
import com.okpost.client.ClientService;
import com.okpost.commun.ApiException;
import com.okpost.config.OkpostProperties;
import com.okpost.securite.Session;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class ComptesSociauxService {

    public record CompteVue(UUID id, String reseau, String nom, String compteExterneId, boolean actif, String erreur) {}

    public static final String FACEBOOK = "FACEBOOK";
    public static final String INSTAGRAM = "INSTAGRAM";

    private final CompteSocialRepository comptes;
    private final ClientService clients;
    private final ClientRepository depotClients;
    private final MetaApi meta;
    private final Chiffreur chiffreur;
    private final OkpostProperties props;

    public ComptesSociauxService(CompteSocialRepository comptes, ClientService clients, ClientRepository depotClients,
                                 MetaApi meta, Chiffreur chiffreur, OkpostProperties props) {
        this.comptes = comptes;
        this.clients = clients;
        this.depotClients = depotClients;
        this.meta = meta;
        this.chiffreur = chiffreur;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public List<CompteVue> lister(Session s, UUID clientId) {
        clients.charger(s, clientId);
        return comptes.findByClientIdOrderByNom(clientId).stream().map(ComptesSociauxService::vue).toList();
    }

    /** Adresse « Se connecter avec Facebook » ; l'état signé ramène au bon client après le retour de Facebook. */
    public String urlConnexionFacebook(Session s, UUID clientId) {
        clients.charger(s, clientId);
        if (!meta.oauthConfigure()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La connexion Facebook n'est pas configurée sur le serveur (META_APP_ID et META_APP_SECRET). Utilise le jeton manuel.");
        }
        String charge = clientId + "|" + s.workspaceId() + "|" + Instant.now().plusSeconds(900).getEpochSecond();
        String etat = Base64.getUrlEncoder().withoutPadding().encodeToString(charge.getBytes(StandardCharsets.UTF_8))
                + "." + signer(charge);
        return meta.urlConnexion(etat, redirection());
    }

    /** Retour de Facebook : enregistre les Pages et renvoie l'id du client pour la redirection. */
    @Transactional
    public UUID retourFacebook(String code, String etat) {
        String[] morceaux = etat == null ? new String[0] : etat.split("\\.");
        if (morceaux.length != 2) throw ApiException.requeteInvalide("Retour Facebook invalide");
        String charge = new String(Base64.getUrlDecoder().decode(morceaux[0]), StandardCharsets.UTF_8);
        if (!signer(charge).equals(morceaux[1])) throw ApiException.interdit("Retour Facebook non reconnu");
        String[] champs = charge.split("\\|");
        if (Instant.now().getEpochSecond() > Long.parseLong(champs[2])) {
            throw ApiException.requeteInvalide("Connexion expirée, recommence depuis la fiche client");
        }
        UUID clientId = UUID.fromString(champs[0]);
        UUID workspaceId = UUID.fromString(champs[1]);
        Client c = depotClients.findByIdAndWorkspaceId(clientId, workspaceId)
                .orElseThrow(() -> ApiException.introuvable("Client"));
        enregistrerPages(c, meta.pages(meta.jetonDepuisCode(code, redirection())));
        return clientId;
    }

    /** Variante sans connexion Facebook : jeton utilisateur (de préférence prolongé) ou jeton de Page collé. */
    @Transactional
    public List<CompteVue> relierAvecJeton(Session s, UUID clientId, String jeton) {
        Client c = clients.charger(s, clientId);
        List<MetaApi.Page> pages;
        try {
            pages = meta.pages(meta.prolonger(jeton.trim()));
        } catch (MetaApi.MetaException e) {
            throw ApiException.requeteInvalide(e.getMessage());
        }
        enregistrerPages(c, pages);
        return lister(s, clientId);
    }

    @Transactional
    public List<CompteVue> activer(Session s, UUID clientId, UUID compteId) {
        clients.charger(s, clientId);
        CompteSocial choisi = comptes.findByIdAndClientId(compteId, clientId).orElseThrow(() -> ApiException.introuvable("Compte"));
        comptes.findByClientIdAndReseau(clientId, choisi.getReseau()).forEach(x -> x.setActif(x.getId().equals(compteId)));
        return lister(s, clientId);
    }

    @Transactional
    public void supprimer(Session s, UUID clientId, UUID compteId) {
        clients.charger(s, clientId);
        comptes.delete(comptes.findByIdAndClientId(compteId, clientId).orElseThrow(() -> ApiException.introuvable("Compte")));
    }

    private void enregistrerPages(Client c, List<MetaApi.Page> pages) {
        if (pages.isEmpty()) {
            throw ApiException.requeteInvalide("Aucune Page trouvée : coche la Page du client lors de l'autorisation Facebook.");
        }
        List<CompteSocial> existants = comptes.findByClientIdAndReseau(c.getId(), FACEBOOK);
        boolean dejaActif = existants.stream().anyMatch(CompteSocial::isActif);
        for (MetaApi.Page p : pages) {
            CompteSocial compte = existants.stream().filter(x -> x.getCompteExterneId().equals(p.id())).findFirst()
                    .orElseGet(CompteSocial::new);
            compte.setWorkspaceId(c.getWorkspaceId());
            compte.setClientId(c.getId());
            compte.setReseau(FACEBOOK);
            compte.setCompteExterneId(p.id());
            compte.setNom(p.nom());
            compte.setJetonChiffre(chiffreur.chiffrer(p.jeton()));
            compte.setErreur(null);
            if (!dejaActif && pages.size() == 1) compte.setActif(true);
            comptes.save(compte);
            meta.compteInstagram(p.id(), p.jeton()).ifPresent(ig -> enregistrerInstagram(c, ig, p.jeton(), pages.size() == 1));
        }
    }

    /** Le compte Instagram professionnel lié à la Page : il publie avec le jeton de la Page. */
    private void enregistrerInstagram(Client c, MetaApi.CompteInstagram ig, String jetonPage, boolean pageUnique) {
        List<CompteSocial> existants = comptes.findByClientIdAndReseau(c.getId(), INSTAGRAM);
        boolean dejaActif = existants.stream().anyMatch(CompteSocial::isActif);
        CompteSocial compte = existants.stream().filter(x -> x.getCompteExterneId().equals(ig.id())).findFirst()
                .orElseGet(CompteSocial::new);
        compte.setWorkspaceId(c.getWorkspaceId());
        compte.setClientId(c.getId());
        compte.setReseau(INSTAGRAM);
        compte.setCompteExterneId(ig.id());
        compte.setNom(ig.nom());
        compte.setJetonChiffre(chiffreur.chiffrer(jetonPage));
        if (!dejaActif && pageUnique) compte.setActif(true);
        comptes.save(compte);
    }

    private String redirection() {
        return props.urlPublique() + "/api/v1/public/meta/retour";
    }

    private String signer(String charge) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(("meta-etat:" + props.jwt().secret()).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(charge.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static CompteVue vue(CompteSocial c) {
        return new CompteVue(c.getId(), c.getReseau(), c.getNom(), c.getCompteExterneId(), c.isActif(), c.getErreur());
    }
}
