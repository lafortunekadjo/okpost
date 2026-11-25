package com.okpost.client;

import com.okpost.abonnement.AbonnementService;
import com.okpost.commun.ApiException;
import com.okpost.securite.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Décrit brièvement un produit d'après une photo de la banque d'images du client. Le résultat est une proposition à ajuster. */
@Service
public class DescriptionProduitService {

    private static final Logger log = LoggerFactory.getLogger(DescriptionProduitService.class);
    private static final int APPELS_PAR_HEURE = 60;
    static final int LONGUEUR_MAX = 500;

    private final ClientService clients;
    private final ClientAssetRepository assets;
    private final AnalyseurPhoto analyseur;
    private final AbonnementService abonnements;
    private final Map<UUID, ArrayDeque<Instant>> appels = new ConcurrentHashMap<>();

    public DescriptionProduitService(ClientService clients, ClientAssetRepository assets, AnalyseurPhoto analyseur, AbonnementService abonnements) {
        this.clients = clients;
        this.assets = assets;
        this.analyseur = analyseur;
        this.abonnements = abonnements;
    }

    public String decrire(Session s, UUID clientId, UUID assetId, String nom) {
        abonnements.exigerEcriture(s.workspaceId());
        Client c = clients.charger(s, clientId);
        ClientAsset photo = assets.findByIdAndClientId(assetId, clientId).orElseThrow(() -> ApiException.introuvable("Photo"));
        if (photo.getType() != ClientAsset.Type.PHOTO || photo.getMime() == null || photo.getMime().contains("svg")) {
            throw ApiException.requeteInvalide("Choisis une photo du produit (pas un logo)");
        }
        if (!analyseur.configure()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "La description par image n'est pas disponible : la clé Gemini n'est pas configurée sur le serveur.");
        }
        limiter(s.workspaceId());
        try {
            String texte = ReformulationService.nettoyerSortie(analyseur.decrireProduit(photo, nom, c.getLangue()), LONGUEUR_MAX);
            if (texte.isEmpty()) throw new ApiException(HttpStatus.BAD_GATEWAY, "L'IA n'a pas renvoyé de description : réessaie");
            return texte;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Description du produit d'après la photo {} impossible : {}", assetId, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "L'IA n'a pas pu décrire cette photo : réessaie ou choisis une photo plus nette.");
        }
    }

    private void limiter(UUID workspaceId) {
        ArrayDeque<Instant> file = appels.computeIfAbsent(workspaceId, k -> new ArrayDeque<>());
        synchronized (file) {
            Instant limite = Instant.now().minus(Duration.ofHours(1));
            while (!file.isEmpty() && file.peekFirst().isBefore(limite)) file.pollFirst();
            if (file.size() >= APPELS_PAR_HEURE) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Trop de descriptions par image : réessaie dans quelques minutes");
            }
            file.addLast(Instant.now());
        }
    }
}
