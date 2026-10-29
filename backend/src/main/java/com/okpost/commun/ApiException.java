package com.okpost.commun;

import org.springframework.http.HttpStatus;

/** Erreur métier renvoyée au client avec un message en français lisible. */
public class ApiException extends RuntimeException {

    private final HttpStatus statut;

    public ApiException(HttpStatus statut, String message) {
        super(message);
        this.statut = statut;
    }

    public HttpStatus getStatut() { return statut; }

    public static ApiException introuvable(String quoi) {
        return new ApiException(HttpStatus.NOT_FOUND, quoi + " introuvable");
    }

    public static ApiException requeteInvalide(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException interdit(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }
}
