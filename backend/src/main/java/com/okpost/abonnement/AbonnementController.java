package com.okpost.abonnement;

import com.okpost.securite.Session;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/abonnements")
public class AbonnementController {

    public record AbonnementVue(String formule, LocalDate debut, LocalDate fin, String statut, String mode,
                                int maxClients, int maxMembres, int calendriersParMois, long prixMensuelXaf) {}

    private final AbonnementService service;

    public AbonnementController(AbonnementService service) {
        this.service = service;
    }

    @GetMapping("/courant")
    public AbonnementVue courant() {
        Abonnement a = service.courant(Session.courante().workspaceId());
        Formule f = a.getFormule();
        return new AbonnementVue(f.name(), a.getDebut(), a.getFin(), a.statutAu(LocalDate.now()).name(),
                a.getMode().name(), f.maxClients(), f.maxMembres(), f.calendriersParMois(), f.prixMensuelXaf());
    }
}
