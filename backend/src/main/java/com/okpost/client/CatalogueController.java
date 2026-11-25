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
    private final CatalogueSiteService site;

    public CatalogueController(CatalogueService service, CatalogueSiteService site) {
        this.service = service;
        this.site = site;
    }

    /** Propose les produits du site web du client (boutique Shopify ou WooCommerce, pages produit, ou texte lu par l'IA). */
    @PostMapping("/depuis-site")
    public CatalogueImport.Resultat depuisSite(@PathVariable UUID id, @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody ClientDtos.DemandeSite d) {
        return site.lire(Session.courante(), id, d.url());
    }

    /** Lit un catalogue CSV et le renvoie sous forme de produits à relire : rien n'est enregistré. */
    @PostMapping("/importer")
    public ResultatImport importer(@PathVariable UUID id, @RequestParam("fichier") MultipartFile fichier) {
        return service.importer(Session.courante(), id, fichier);
    }
}
