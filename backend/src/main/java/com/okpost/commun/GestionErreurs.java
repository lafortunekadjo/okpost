package com.okpost.commun;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/** Toutes les erreurs au format application/problem+json (RFC 9457). */
@RestControllerAdvice
public class GestionErreurs {

    private static final Logger log = LoggerFactory.getLogger(GestionErreurs.class);

    @ExceptionHandler(ApiException.class)
    public ProblemDetail api(ApiException e) {
        return ProblemDetail.forStatusAndDetail(e.getStatut(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " : " + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Champs invalides — " + detail);
    }

    @ExceptionHandler(com.okpost.ia.GenerationException.class)
    public ProblemDetail ia(com.okpost.ia.GenerationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail tropGros(MaxUploadSizeExceededException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "Fichier trop lourd (25 Mo maximum pour une vidéo)");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail refuse(AccessDeniedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Accès refusé");
    }

    @ExceptionHandler(org.springframework.web.ErrorResponseException.class)
    public ProblemDetail springWeb(org.springframework.web.ErrorResponseException e) {
        return e.getBody();
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ProblemDetail illisible(org.springframework.http.converter.HttpMessageNotReadableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Requête illisible : vérifie le format des données envoyées");
    }

    /**
     * Le navigateur a interrompu le téléchargement (lecture vidéo, page fermée) : rien à répondre ni à signaler.
     * Sans ce gestionnaire, le serveur tente d'écrire un message d'erreur dans une réponse vidéo déjà commencée.
     */
    @ExceptionHandler({org.springframework.web.context.request.async.AsyncRequestNotUsableException.class,
            org.apache.catalina.connector.ClientAbortException.class})
    public void connexionInterrompue(Exception e) {
        log.debug("Connexion interrompue par le navigateur : {}", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail inattendu(Exception e) {
        if (String.valueOf(e.getMessage()).contains("Connection reset") || String.valueOf(e.getMessage()).contains("Broken pipe")) {
            log.debug("Connexion interrompue : {}", e.getMessage());
            return null;
        }
        log.error("Erreur inattendue", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Une erreur inattendue est survenue. Réessaie dans un instant.");
    }
}
