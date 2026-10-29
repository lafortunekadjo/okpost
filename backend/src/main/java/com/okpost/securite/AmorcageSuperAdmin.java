package com.okpost.securite;

import com.okpost.abonnement.Abonnement;
import com.okpost.abonnement.AbonnementRepository;
import com.okpost.abonnement.Formule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** Crée le compte super-admin au premier démarrage si SUPERADMIN_EMAIL et SUPERADMIN_PASSWORD sont fournis. */
@Component
public class AmorcageSuperAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AmorcageSuperAdmin.class);

    private final UtilisateurRepository utilisateurs;
    private final WorkspaceRepository workspaces;
    private final AbonnementRepository abonnements;
    private final PasswordEncoder encodeur;
    private final String email;
    private final String motDePasse;

    public AmorcageSuperAdmin(UtilisateurRepository utilisateurs, WorkspaceRepository workspaces,
                              AbonnementRepository abonnements, PasswordEncoder encodeur,
                              @Value("${SUPERADMIN_EMAIL:}") String email,
                              @Value("${SUPERADMIN_PASSWORD:}") String motDePasse) {
        this.utilisateurs = utilisateurs;
        this.workspaces = workspaces;
        this.abonnements = abonnements;
        this.encodeur = encodeur;
        this.email = email;
        this.motDePasse = motDePasse;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() || motDePasse.isBlank() || utilisateurs.existsByEmailIgnoreCase(email)) {
            return;
        }
        if (motDePasse.length() < 12) {
            log.warn("SUPERADMIN_PASSWORD doit faire au moins 12 caractères : super-admin non créé");
            return;
        }
        Workspace ws = new Workspace();
        ws.setNom("OkPost");
        workspaces.save(ws);

        Utilisateur u = new Utilisateur();
        u.setWorkspaceId(ws.getId());
        u.setEmail(email.trim().toLowerCase());
        u.setNom("Super-admin");
        u.setMotDePasseHash(encodeur.encode(motDePasse));
        u.setRole(Role.SUPER_ADMIN);
        utilisateurs.save(u);

        Abonnement a = new Abonnement();
        a.setWorkspaceId(ws.getId());
        a.setFormule(Formule.AGENCE);
        a.setDebut(LocalDate.now());
        a.setFin(LocalDate.now().plusYears(50));
        a.setStatut(Abonnement.Statut.ACTIF);
        a.setMode(Abonnement.Mode.ESPECES);
        abonnements.save(a);
        log.info("Compte super-admin créé pour {}", email);
    }
}
