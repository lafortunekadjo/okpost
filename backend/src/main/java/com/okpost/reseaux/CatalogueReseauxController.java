package com.okpost.reseaux;

import com.okpost.client.CatalogueImport;
import com.okpost.securite.Session;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients/{id}/catalogue")
public class CatalogueReseauxController {

    private final CatalogueReseauxService service;

    public CatalogueReseauxController(CatalogueReseauxService service) {
        this.service = service;
    }

    /** Propose les produits cités dans les publications Facebook et Instagram reliées : rien n'est enregistré. */
    @PostMapping("/depuis-reseaux")
    public CatalogueImport.Resultat depuisReseaux(@PathVariable UUID id) {
        return service.proposer(Session.courante(), id);
    }
}
