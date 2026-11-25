package com.okpost.client;

import com.okpost.securite.Session;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reformuler")
public class ReformulationController {

    /** champ : « produit » (500 caractères au plus) ou « activite » (description de l'entreprise, 1 500). clientId est facultatif. */
    public record Demande(@NotBlank @Size(max = 2000) String texte, @Size(max = 150) String sujet,
                          @Pattern(regexp = "CORRIGER|REFORMULER|RACCOURCIR") String mode,
                          @Pattern(regexp = "produit|activite") String champ, UUID clientId) {}

    public record Reponse(String texte) {}

    private final ReformulationService service;

    public ReformulationController(ReformulationService service) {
        this.service = service;
    }

    @PostMapping
    public Reponse reformuler(@Valid @RequestBody Demande d) {
        int max = "activite".equals(d.champ()) ? 1500 : 500;
        return new Reponse(service.reformuler(Session.courante(), d.clientId(), d.texte(), d.sujet(), d.mode(), max));
    }
}
