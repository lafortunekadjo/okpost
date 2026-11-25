package com.okpost.securite;

import com.okpost.abonnement.AbonnementService;
import com.okpost.commun.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Membres de l'agence. Le MVP crée le compte avec un mot de passe provisoire transmis par l'admin. */
@RestController
@RequestMapping("/api/v1/membres")
public class MembresController {

    public record Membre(UUID id, String nom, String email, String role, boolean actif) {}

    public record NouveauMembre(@NotBlank @Size(max = 150) String nom, @NotBlank @Email String email,
                                @NotBlank @Size(min = 8, max = 100) String motDePasseProvisoire) {}

    private final UtilisateurRepository utilisateurs;
    private final AbonnementService abonnements;
    private final PasswordEncoder encodeur;
    private final com.okpost.admin.JournalService journal;

    public MembresController(UtilisateurRepository utilisateurs, AbonnementService abonnements, PasswordEncoder encodeur,
                             com.okpost.admin.JournalService journal) {
        this.journal = journal;
        this.utilisateurs = utilisateurs;
        this.abonnements = abonnements;
        this.encodeur = encodeur;
    }

    @GetMapping
    public List<Membre> lister() {
        Session s = Session.courante();
        s.exigerProprietaire();
        return utilisateurs.findByWorkspaceIdOrderByNom(s.workspaceId()).stream().map(MembresController::vue).toList();
    }

    @PostMapping
    @Transactional
    public Membre inviter(@Valid @RequestBody NouveauMembre n) {
        Session s = Session.courante();
        s.exigerAdmin();
        abonnements.exigerEcriture(s.workspaceId());
        long actifs = utilisateurs.findByWorkspaceIdOrderByNom(s.workspaceId()).stream().filter(Utilisateur::isActif).count();
        int max = abonnements.formule(s.workspaceId()).maxMembres();
        if (actifs >= max) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "Ta formule permet " + max + " membre(s). Passe à la formule supérieure.");
        }
        if (utilisateurs.existsByEmailIgnoreCase(n.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "Un compte existe déjà avec cet e-mail");
        }
        Utilisateur u = new Utilisateur();
        u.setWorkspaceId(s.workspaceId());
        u.setNom(n.nom());
        u.setEmail(n.email().trim().toLowerCase());
        u.setMotDePasseHash(encodeur.encode(n.motDePasseProvisoire()));
        u.setRole(Role.CM);
        Utilisateur cree = utilisateurs.save(u);
        journal.enregistrer(s, com.okpost.admin.Actions.MEMBRE_INVITE, cree.getNom(), cree.getEmail());
        return vue(cree);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void retirer(@PathVariable UUID id) {
        Session s = Session.courante();
        s.exigerAdmin();
        Utilisateur u = utilisateurs.findByIdAndWorkspaceId(id, s.workspaceId())
                .orElseThrow(() -> ApiException.introuvable("Membre"));
        if (u.getId().equals(s.userId())) {
            throw ApiException.requeteInvalide("Tu ne peux pas te retirer toi-même");
        }
        u.setActif(false);
        utilisateurs.save(u);
    }

    private static Membre vue(Utilisateur u) {
        return new Membre(u.getId(), u.getNom(), u.getEmail(), u.getRole().name(), u.isActif());
    }
}
