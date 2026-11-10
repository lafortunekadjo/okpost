package com.okpost.client;

import com.okpost.commun.ApiException;
import com.okpost.config.OkpostProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class StockageDisque implements StockageFichiers {

    private static final Pattern NOM_VALIDE = Pattern.compile("^[0-9a-f-]{36}\\.(png|jpg|webp|svg|mp4|mov|webm)$");

    private final Path racine;

    public StockageDisque(OkpostProperties props) throws IOException {
        this.racine = Path.of(props.fichiers().dossier()).toAbsolutePath().normalize();
        Files.createDirectories(racine);
    }

    @Override
    public String enregistrer(InputStream contenu, String extension) {
        String nom = UUID.randomUUID() + "." + extension;
        try {
            Files.copy(contenu, racine.resolve(nom), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return nom;
    }

    @Override
    public Resource lire(String chemin) {
        return new FileSystemResource(resoudre(chemin));
    }

    @Override
    public void supprimer(String chemin) {
        try {
            Files.deleteIfExists(resoudre(chemin));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path resoudre(String chemin) {
        if (!NOM_VALIDE.matcher(chemin).matches()) {
            throw ApiException.introuvable("Fichier");
        }
        return racine.resolve(chemin);
    }
}
