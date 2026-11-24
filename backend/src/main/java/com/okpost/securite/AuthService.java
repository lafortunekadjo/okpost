package com.okpost.securite;

import com.okpost.abonnement.AbonnementService;
import com.okpost.commun.ApiException;
import com.okpost.config.OkpostProperties;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class AuthService {

    public record Jetons(String acces, String rafraichissement, Utilisateur utilisateur) {}

    private static final int TENTATIVES_MAX = 5;
    private static final Duration DUREE_BLOCAGE = Duration.ofMinutes(15);

    private final UtilisateurRepository utilisateurs;
    private final WorkspaceRepository workspaces;
    private final RefreshTokenRepository rafraichissements;
    private final AbonnementService abonnements;
    private final PasswordEncoder encodeur;
    private final JwtService jwt;
    private final OkpostProperties props;
    private final com.okpost.admin.JournalService journal;

    public AuthService(UtilisateurRepository utilisateurs, WorkspaceRepository workspaces,
                       RefreshTokenRepository rafraichissements, AbonnementService abonnements,
                       PasswordEncoder encodeur, JwtService jwt, OkpostProperties props,
                       com.okpost.admin.JournalService journal) {
        this.journal = journal;
        this.utilisateurs = utilisateurs;
        this.workspaces = workspaces;
        this.rafraichissements = rafraichissements;
        this.abonnements = abonnements;
        this.encodeur = encodeur;
        this.jwt = jwt;
        this.props = props;
    }

    @Transactional
    public Jetons inscrire(String nomEspace, String nom, String email, String motDePasse) {
        if (utilisateurs.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Un compte existe déjà avec cet e-mail");
        }
        Workspace ws = new Workspace();
        ws.setNom(nomEspace);
        workspaces.save(ws);

        Utilisateur u = new Utilisateur();
        u.setWorkspaceId(ws.getId());
        u.setNom(nom);
        u.setEmail(email.trim().toLowerCase());
        u.setMotDePasseHash(encodeur.encode(motDePasse));
        u.setRole(Role.ADMIN);
        utilisateurs.save(u);

        abonnements.creerEssai(ws.getId());
        journal.enregistrer(ws.getId(), u.getId(), null, com.okpost.admin.Actions.INSCRIPTION, nomEspace, u.getEmail());
        return emettre(u);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Jetons connecter(String email, String motDePasse) {
        Utilisateur u = utilisateurs.findByEmailIgnoreCase(email.trim())
                .orElseThrow(AuthService::identifiantsInvalides);
        Instant maintenant = Instant.now();
        if (u.getBloqueJusqu() != null && u.getBloqueJusqu().isAfter(maintenant)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Trop de tentatives : compte bloqué 15 minutes");
        }
        if (!u.isActif()) {
            throw ApiException.interdit("Ce compte est désactivé");
        }
        if (!encodeur.matches(motDePasse, u.getMotDePasseHash())) {
            u.setTentativesEchouees(u.getTentativesEchouees() + 1);
            if (u.getTentativesEchouees() >= TENTATIVES_MAX) {
                u.setBloqueJusqu(maintenant.plus(DUREE_BLOCAGE));
                u.setTentativesEchouees(0);
            }
            utilisateurs.save(u);
            journal.enregistrer(u.getWorkspaceId(), u.getId(), null, com.okpost.admin.Actions.CONNEXION_ECHEC, u.getEmail(), "Mot de passe incorrect");
            throw identifiantsInvalides();
        }
        u.setTentativesEchouees(0);
        u.setBloqueJusqu(null);
        utilisateurs.save(u);
        journal.enregistrer(u.getWorkspaceId(), u.getId(), null, com.okpost.admin.Actions.CONNEXION, u.getEmail(), null);
        return emettre(u);
    }

    @Transactional
    public Jetons rafraichir(String jetonRafraichissement) {
        RefreshToken ancien = rafraichissements.findByJetonHash(JwtService.sha256(jetonRafraichissement))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session expirée"));
        rafraichissements.delete(ancien);
        if (ancien.getExpireLe().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Session expirée");
        }
        Utilisateur u = utilisateurs.findById(ancien.getUserId())
                .filter(Utilisateur::isActif)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session expirée"));
        return emettre(u);
    }

    @Transactional
    public void deconnecter(String jetonRafraichissement) {
        if (jetonRafraichissement != null) {
            rafraichissements.findByJetonHash(JwtService.sha256(jetonRafraichissement))
                    .ifPresent(rafraichissements::delete);
        }
    }

    private Jetons emettre(Utilisateur u) {
        String brut = JwtService.jetonAleatoire();
        Instant expiration = Instant.now().plus(Duration.ofDays(props.jwt().rafraichissementJours()));
        rafraichissements.save(new RefreshToken(u.getId(), JwtService.sha256(brut), expiration));
        return new Jetons(jwt.jetonAcces(u), brut, u);
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void nettoyer() {
        rafraichissements.supprimerExpires(Instant.now());
    }

    private static ApiException identifiantsInvalides() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "E-mail ou mot de passe incorrect");
    }
}
