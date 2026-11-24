package com.okpost.validation;

import com.okpost.securite.Session;
import com.okpost.validation.CodesValidationService.ContactAvecCode;
import com.okpost.validation.CodesValidationService.ContactVue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Personnes autorisées à valider pour un client, et leurs codes personnels. */
@RestController
@RequestMapping("/api/v1/clients/{clientId}/contacts-validation")
public class ContactsValidationController {

    public record NouveauContact(@NotBlank @Size(max = 150) String nom) {}

    private final CodesValidationService service;

    public ContactsValidationController(CodesValidationService service) {
        this.service = service;
    }

    @GetMapping
    public List<ContactVue> lister(@PathVariable UUID clientId) {
        return service.lister(Session.courante(), clientId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContactAvecCode creer(@PathVariable UUID clientId, @Valid @RequestBody NouveauContact n) {
        return service.creer(Session.courante(), clientId, n.nom());
    }

    @PostMapping("/{id}/nouveau-code")
    public ContactAvecCode renouveler(@PathVariable UUID clientId, @PathVariable UUID id) {
        return service.renouveler(Session.courante(), clientId, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable UUID clientId, @PathVariable UUID id) {
        service.supprimer(Session.courante(), clientId, id);
    }
}
