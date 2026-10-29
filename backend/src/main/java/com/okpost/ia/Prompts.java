package com.okpost.ia;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Modèles de prompts versionnés dans resources/prompts, avec des variables {{nom}}. */
@Component
public class Prompts {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String rendre(String nom, Map<String, String> variables) {
        String modele = cache.computeIfAbsent(nom, Prompts::charger);
        String resultat = modele;
        for (Map.Entry<String, String> v : variables.entrySet()) {
            resultat = resultat.replace("{{" + v.getKey() + "}}", v.getValue() == null ? "—" : v.getValue());
        }
        return resultat;
    }

    private static String charger(String nom) {
        try {
            return new ClassPathResource("prompts/" + nom + ".txt").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Prompt introuvable : " + nom, e);
        }
    }
}
