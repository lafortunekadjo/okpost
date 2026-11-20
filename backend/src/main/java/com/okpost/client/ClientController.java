package com.okpost.client;

import com.okpost.client.ClientDtos.AssetVue;
import com.okpost.client.ClientDtos.ClientForm;
import com.okpost.client.ClientDtos.ClientResume;
import com.okpost.client.ClientDtos.ClientVue;
import com.okpost.securite.Session;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {

    private final ClientService service;
    private final TableauDeBord tableau;
    private final AnalyseurPhoto analyseur;
    private final FicheDepuisSiteService ficheDepuisSite;
    private final ImportImageService importImage;

    public ClientController(ClientService service, TableauDeBord tableau, AnalyseurPhoto analyseur,
                            FicheDepuisSiteService ficheDepuisSite, ImportImageService importImage) {
        this.ficheDepuisSite = ficheDepuisSite;
        this.importImage = importImage;
        this.service = service;
        this.tableau = tableau;
        this.analyseur = analyseur;
    }

    /** Propose une fiche à partir du site web (rien n'est enregistré : l'utilisateur relit puis enregistre). */
    @PostMapping("/analyser-site")
    public FicheDepuisSiteService.FicheProposee analyserSite(@Valid @RequestBody ClientDtos.DemandeSite d) {
        return ficheDepuisSite.proposer(Session.courante(), d.url(), Boolean.TRUE.equals(d.seulementImages()));
    }

    @GetMapping
    public List<ClientResume> lister() {
        Session s = Session.courante();
        return service.lister(s).stream().map(tableau::resume).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientVue creer(@Valid @RequestBody ClientForm form) {
        return service.vue(service.creer(Session.courante(), form));
    }

    @GetMapping("/{id}")
    public ClientVue lire(@PathVariable UUID id) {
        return service.vue(service.charger(Session.courante(), id));
    }

    @PutMapping("/{id}")
    public ClientVue modifier(@PathVariable UUID id, @Valid @RequestBody ClientForm form) {
        return service.vue(service.modifier(Session.courante(), id, form));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiver(@PathVariable UUID id) {
        service.archiver(Session.courante(), id);
    }

    @PostMapping("/{id}/assets")
    @ResponseStatus(HttpStatus.CREATED)
    public AssetVue ajouterFichier(@PathVariable UUID id,
                                   @RequestParam("type") ClientAsset.Type type,
                                   @RequestParam(value = "legende", required = false) String legende,
                                   @RequestParam("fichier") MultipartFile fichier) {
        ClientAsset a = service.ajouterFichier(Session.courante(), id, type, legende, fichier);
        if (a.getType() == ClientAsset.Type.PHOTO) {
            analyseur.analyser(a.getId());
        }
        return AssetVue.de(a);
    }

    /** Importe dans la fiche une image trouvée sur le site du client (logo, ou photo pour la banque d'images). */
    @PostMapping("/{id}/assets/depuis-url")
    @ResponseStatus(HttpStatus.CREATED)
    public AssetVue importerImage(@PathVariable UUID id, @Valid @RequestBody ClientDtos.DemandeImportImage d) {
        return AssetVue.de(importImage.importer(Session.courante(), id, d.type(), d.url()));
    }

    @PatchMapping("/{id}/assets/{assetId}")
    public AssetVue modifierFichier(@PathVariable UUID id, @PathVariable UUID assetId,
                                    @Valid @RequestBody ClientDtos.AssetModif m) {
        return AssetVue.de(service.modifierFichier(Session.courante(), id, assetId, m));
    }

    /** Relance la description automatique d'une photo (asynchrone : l'état passe à EN_COURS puis OK ou ECHEC). */
    @PostMapping("/{id}/assets/{assetId}/analyser")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AssetVue analyser(@PathVariable UUID id, @PathVariable UUID assetId) {
        ClientAsset a = service.photo(Session.courante(), id, assetId);
        if (a.getType() != ClientAsset.Type.PHOTO) {
            throw com.okpost.commun.ApiException.requeteInvalide("Seules les photos sont décrites");
        }
        analyseur.analyser(a.getId());
        return AssetVue.de(a);
    }

    @DeleteMapping("/{id}/assets/{assetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimerFichier(@PathVariable UUID id, @PathVariable UUID assetId) {
        service.supprimerFichier(Session.courante(), id, assetId);
    }
}
