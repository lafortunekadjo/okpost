package com.okpost.client;

import com.okpost.client.CatalogueService.ResultatImport;
import com.okpost.securite.Session;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients/{id}/catalogue")
public class CatalogueController {

    private final CatalogueService service;

    public CatalogueController(CatalogueService service) {
        this.service = service;
    }

    /** Lit un catalogue CSV et le renvoie sous forme de produits à relire : rien n'est enregistré. */
    @PostMapping("/importer")
    public ResultatImport importer(@PathVariable UUID id, @RequestParam("fichier") MultipartFile fichier) {
        return service.importer(Session.courante(), id, fichier);
    }
}
