package com.okpost.admin;

import com.okpost.abonnement.Abonnement;
import com.okpost.abonnement.Formule;
import com.okpost.abonnement.FormulesService;
import com.okpost.admin.AdminService.FormuleAdmin;
import com.okpost.admin.AdminService.PageJournal;
import com.okpost.admin.AdminService.VueComptes;
import com.okpost.commun.ApiException;
import com.okpost.securite.Session;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Espace super-admin : réservé au propriétaire de la plateforme. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    public record DemandeFormule(@NotBlank @Size(max = 40) String nom, @Min(0) @Max(10_000_000) long prixMensuelXaf,
                                 @Min(1) @Max(1000) int maxClients, @Min(1) @Max(100) int maxMembres,
                                 @Min(1) @Max(1000) int calendriersParMois, @Min(0) @Max(10_000) int imagesParMois) {}

    public record DemandeAbonnement(@NotNull Formule formule, @NotNull LocalDate fin) {}

    public record AbonnementVue(String formule, LocalDate debut, LocalDate fin, String statut, String mode) {}

    private final AdminService service;
    private final FormulesService formules;

    public AdminController(AdminService service, FormulesService formules) {
        this.service = service;
        this.formules = formules;
    }

    @GetMapping("/comptes")
    public VueComptes comptes() {
        Session.courante().exigerSuperAdmin();
        return service.comptes();
    }

    /** Attribue une formule à un compte jusqu'à une date (voir AdminService#changerAbonnement). */
    @PutMapping("/comptes/{id}/abonnement")
    public AbonnementVue changerAbonnement(@PathVariable UUID id, @Valid @RequestBody DemandeAbonnement d) {
        Abonnement a = service.changerAbonnement(Session.courante(), id, d.formule(), d.fin());
        return new AbonnementVue(a.getFormule().name(), a.getDebut(), a.getFin(), a.statutAu(LocalDate.now()).name(), a.getMode().name());
    }

    @GetMapping("/formules")
    public List<FormuleAdmin> formules() {
        Session.courante().exigerSuperAdmin();
        return service.formules();
    }

    /** Modifie le nom, le prix et les limites d'une formule : cela s'applique tout de suite à tous les comptes qui l'ont. */
    @PutMapping("/formules/{code}")
    public FormuleAdmin modifierFormule(@PathVariable String code, @Valid @RequestBody DemandeFormule d) {
        Session s = Session.courante();
        s.exigerSuperAdmin();
        Formule f;
        try {
            f = Formule.valueOf(code.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.introuvable("Formule");
        }
        formules.modifier(s, f, new FormulesService.Modification(d.nom(), d.prixMensuelXaf(), d.maxClients(), d.maxMembres(),
                d.calendriersParMois(), d.imagesParMois()));
        return service.formules().stream().filter(x -> x.code().equals(f.name())).findFirst().orElseThrow();
    }

    @GetMapping("/journal")
    public PageJournal journal(@RequestParam(required = false) UUID espace, @RequestParam(required = false) String action,
                               @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int taille) {
        Session.courante().exigerSuperAdmin();
        return service.journal(espace, action, page, taille);
    }
}
