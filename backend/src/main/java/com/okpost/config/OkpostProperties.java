package com.okpost.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "okpost")
public record OkpostProperties(
        String urlPublique,
        String corsOrigines,
        Jwt jwt,
        Fichiers fichiers,
        Mail mail,
        Validation validation,
        Ia ia) {

    public record Jwt(String secret, int accesMinutes, int rafraichissementJours) {}

    public record Fichiers(String dossier) {}

    public record Mail(String expediteur, boolean actif) {}

    public record Validation(int dureeJours) {}

    public record Ia(String fournisseur, int debitParMinute, int lotDetails, Gemini gemini, Claude claude) {}

    public record Gemini(String apiKey, String baseUrl, String modele) {}

    public record Claude(String apiKey, String modeleStrategie, String modeleRedaction) {}
}
