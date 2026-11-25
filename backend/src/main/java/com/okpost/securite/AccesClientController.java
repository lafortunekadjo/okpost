package com.okpost.securite;

import com.okpost.securite.AccesClientService.Collaborateur;
import com.okpost.securite.AccesClientService.Contexte;
import com.okpost.securite.AccesClientService.Partage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class AccesClientController {

    public record Invitation(@NotBlank @Email String email) {}

    public record DemandeContexte(@NotNull UUID clientId) {}

    private final AccesClientService service;

    public AccesClientController(AccesClientService service) {
        this.service = service;
    }

    // ---- propriétaire : qui collabore sur ce client

    @GetMapping("/clients/{clientId}/collaborateurs")
    public List<Collaborateur> lister(@PathVariable UUID clientId) {
        return service.lister(Session.courante(), clientId);
    }

    @PostMapping("/clients/{clientId}/collaborateurs")
    @ResponseStatus(HttpStatus.CREATED)
    public Collaborateur inviter(@PathVariable UUID clientId, @Valid @RequestBody Invitation i) {
        return service.inviter(Session.courante(), clientId, i.email());
    }

    @DeleteMapping("/clients/{clientId}/collaborateurs/{accesId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retirer(@PathVariable UUID clientId, @PathVariable UUID accesId) {
        service.retirer(Session.courante(), clientId, accesId);
    }

    // ---- invité : ses clients partagés

    @GetMapping("/acces/partages")
    public List<Partage> partages() {
        return service.partages(Session.courante());
    }

    @DeleteMapping("/acces/partages/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quitter(@PathVariable UUID clientId) {
        service.quitter(Session.courante(), clientId);
    }

    /** Ouvre une session limitée à un client partagé : le navigateur remplace son jeton d'accès par celui-ci. */
    @PostMapping("/acces/contexte")
    public AuthController.Reponse contexte(@Valid @RequestBody DemandeContexte d) {
        Contexte c = service.contexte(Session.courante(), d.clientId());
        return new AuthController.Reponse(c.jeton(), c.utilisateur().getId(), c.espaceId(), c.utilisateur().getNom(),
                c.utilisateur().getEmail(), Role.COLLAB.name(), c.clientId());
    }
}
