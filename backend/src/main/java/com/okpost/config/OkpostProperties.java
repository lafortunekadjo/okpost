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
        Ia ia,
        Meta meta,
        String cleChiffrement) {

    /** Application Meta (Facebook / Instagram) pour relier les Pages et publier. */
    public record Meta(String appId, String appSecret, String version, boolean instagram) {}

    public record Jwt(String secret, int accesMinutes, int rafraichissementJours) {}

    public record Fichiers(String dossier) {}

    public record Mail(String expediteur, boolean actif) {}

    public record Validation(int dureeJours) {}

    public record Ia(String fournisseur, int debitParMinute, int lotDetails, int delaiSecondes,
                     Gemini gemini, Claude claude, Compatible compatible, Image image) {}

    /**
     * Génération d'images. fournisseur : moteur par défaut (gemini | cloudflare).
     * Gemini (payant) : modele, taille. Cloudflare Workers AI (quota gratuit quotidien) : compte, jeton, modèle FLUX.
     */
    public record Image(String fournisseur, String modele, String taille,
                        String cloudflareCompte, String cloudflareJeton, String cloudflareModele) {}

    public record Gemini(String apiKey, String baseUrl, String modele) {}

    public record Claude(String apiKey, String modeleStrategie, String modeleRedaction) {}

    /** Tout fournisseur compatible OpenAI : Groq, Cerebras, Mistral, OpenRouter… */
    public record Compatible(String apiKey, String baseUrl, String completionsPath, String modele) {}
}
