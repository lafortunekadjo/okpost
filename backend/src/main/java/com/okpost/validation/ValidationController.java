package com.okpost.validation;

import com.okpost.securite.Session;
import com.okpost.validation.ValidationDtos.EnvoiEmail;
import com.okpost.validation.ValidationDtos.LienVue;
import com.okpost.validation.ValidationDtos.NouveauLien;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/v1")
public class ValidationController {

    private final ValidationService service;

    public ValidationController(ValidationService service) {
        this.service = service;
    }

    @PostMapping("/calendriers/{id}/liens-validation")
    @ResponseStatus(HttpStatus.CREATED)
    public LienVue creer(@PathVariable UUID id, @Valid @RequestBody NouveauLien n) {
        return service.creerLien(Session.courante(), id, n.publications(), n.email());
    }

    @GetMapping("/calendriers/{id}/liens-validation")
    public List<LienVue> lister(@PathVariable UUID id) {
        return service.lister(Session.courante(), id);
    }

    @PostMapping("/liens-validation/{id}/envoyer-email")
    public LienVue envoyer(@PathVariable UUID id, @Valid @RequestBody EnvoiEmail e) {
        return service.envoyerParEmail(Session.courante(), id, e.email());
    }

    @DeleteMapping("/liens-validation/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoquer(@PathVariable UUID id) {
        service.revoquer(Session.courante(), id);
    }
}
