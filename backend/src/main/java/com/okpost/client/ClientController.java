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

    public ClientController(ClientService service, TableauDeBord tableau) {
        this.service = service;
        this.tableau = tableau;
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
        return new AssetVue(a.getId(), a.getType().name(), ClientDtos.urlFichier(a.getChemin()), a.getNomOrigine(), a.getLegende());
    }

    @DeleteMapping("/{id}/assets/{assetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimerFichier(@PathVariable UUID id, @PathVariable UUID assetId) {
        service.supprimerFichier(Session.courante(), id, assetId);
    }
}
