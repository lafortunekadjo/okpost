package com.okpost.admin;

import com.okpost.admin.AdminService.PageJournal;
import com.okpost.admin.AdminService.VueComptes;
import com.okpost.securite.Session;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Espace super-admin : réservé au propriétaire de la plateforme. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    @GetMapping("/comptes")
    public VueComptes comptes() {
        Session.courante().exigerSuperAdmin();
        return service.comptes();
    }

    @GetMapping("/journal")
    public PageJournal journal(@RequestParam(required = false) UUID espace, @RequestParam(required = false) String action,
                               @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int taille) {
        Session.courante().exigerSuperAdmin();
        return service.journal(espace, action, page, taille);
    }
}
