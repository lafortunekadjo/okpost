package com.okpost.securite;

import com.okpost.commun.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/** Utilisateur connecté, lu dans le jeton JWT de la requête courante. */
public record Session(UUID userId, UUID workspaceId, Role role) {

    public static Session courante() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Connexion requise");
        }
        return new Session(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getClaimAsString("ws")),
                Role.valueOf(jwt.getClaimAsString("role")));
    }

    public boolean estAdmin() {
        return role == Role.ADMIN || role == Role.SUPER_ADMIN;
    }

    public void exigerAdmin() {
        if (!estAdmin()) {
            throw ApiException.interdit("Réservé à l'administrateur de l'espace");
        }
    }

    public void exigerSuperAdmin() {
        if (role != Role.SUPER_ADMIN) {
            throw ApiException.interdit("Réservé au super-admin de la plateforme");
        }
    }
}
