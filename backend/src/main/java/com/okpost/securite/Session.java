package com.okpost.securite;

import com.okpost.commun.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Utilisateur connecté, lu dans le jeton JWT de la requête courante.
 *
 * @param clientRestreint pour un collaborateur invité sur un seul client (rôle COLLAB) : le seul client de l'espace du propriétaire
 *                        auquel cette session donne accès ; null pour les autres rôles
 */
public record Session(UUID userId, UUID workspaceId, Role role, UUID clientRestreint) {

    public Session(UUID userId, UUID workspaceId, Role role) {
        this(userId, workspaceId, role, null);
    }

    public static Session courante() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Connexion requise");
        }
        String client = jwt.getClaimAsString("client");
        return new Session(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getClaimAsString("ws")),
                Role.valueOf(jwt.getClaimAsString("role")),
                client == null || client.isBlank() ? null : UUID.fromString(client));
    }

    public boolean estAdmin() {
        return role == Role.ADMIN || role == Role.SUPER_ADMIN;
    }

    /** Invité sur un seul client d'un autre espace : il n'a accès à rien d'autre. */
    public boolean estCollaborateur() {
        return role == Role.COLLAB;
    }

    public void exigerAdmin() {
        if (!estAdmin()) {
            throw ApiException.interdit("Réservé à l'administrateur de l'espace");
        }
    }

    /** Écrans et actions qui concernent tout l'espace (membres, abonnement, archives…) : jamais pour un collaborateur invité. */
    public void exigerProprietaire() {
        if (estCollaborateur()) {
            throw ApiException.interdit("Réservé aux membres de l'espace : tu es invité sur ce client uniquement");
        }
    }

    public void exigerSuperAdmin() {
        if (role != Role.SUPER_ADMIN) {
            throw ApiException.interdit("Réservé au super-admin de la plateforme");
        }
    }
}
