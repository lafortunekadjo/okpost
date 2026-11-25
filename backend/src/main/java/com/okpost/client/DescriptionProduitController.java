package com.okpost.client;

import com.okpost.securite.Session;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients/{id}")
public class DescriptionProduitController {

    public record Demande(@NotNull UUID photoId, @Size(max = 150) String nom) {}

    public record Reponse(String texte) {}

    private final DescriptionProduitService service;

    public DescriptionProduitController(DescriptionProduitService service) {
        this.service = service;
    }

    /** Propose une description brève du produit d'après sa photo : rien n'est enregistré. */
    @PostMapping("/decrire-produit")
    public Reponse decrire(@PathVariable UUID id, @Valid @RequestBody Demande d) {
        return new Reponse(service.decrire(Session.courante(), id, d.photoId(), d.nom()));
    }
}
