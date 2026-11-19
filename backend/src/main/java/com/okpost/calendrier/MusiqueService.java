package com.okpost.calendrier;

import com.okpost.commun.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Bibliothèque de musiques du serveur : des fichiers audio libres de droits, déposés une fois par le super-admin,
 * que chaque carrousel peut choisir au lieu de téléverser sa propre piste. Les pistes sont de simples fichiers dans un dossier.
 */
@Service
public class MusiqueService {

    public record Piste(String id, String nom, long taille, Path chemin) {}

    static final long MAX = 15L * 1024 * 1024;
    private static final Set<String> EXTENSIONS = Set.of("mp3", "m4a", "aac", "ogg", "wav");

    private final Path dossier;

    public MusiqueService(@Value("${okpost.musiques}") String dossier) {
        this.dossier = Path.of(dossier).toAbsolutePath().normalize();
    }

    public List<Piste> lister() {
        if (!Files.isDirectory(dossier)) return List.of();
        try (Stream<Path> fichiers = Files.list(dossier)) {
            return fichiers.filter(Files::isRegularFile)
                    .filter(f -> EXTENSIONS.contains(extension(f.getFileName().toString())))
                    .sorted((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()))
                    .map(f -> new Piste(id(f.getFileName().toString()), nom(f.getFileName().toString()), taille(f), f))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Retrouve une piste par son identifiant, en parcourant la liste : aucun chemin fourni par l'utilisateur n'est utilisé. */
    public Piste resoudre(String id) {
        return lister().stream().filter(p -> p.id().equals(id)).findFirst()
                .orElseThrow(() -> ApiException.introuvable("Musique"));
    }

    public Resource ecoute(Piste p) {
        return new FileSystemResource(p.chemin());
    }

    public Piste ajouter(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) throw ApiException.requeteInvalide("Fichier vide");
        if (fichier.getSize() > MAX) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Musique trop lourde (15 Mo maximum)");
        String original = fichier.getOriginalFilename() == null ? "" : fichier.getOriginalFilename();
        String ext = extension(original);
        if (!EXTENSIONS.contains(ext)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formats acceptés : MP3, M4A, AAC, OGG ou WAV");
        }
        byte[] octets;
        try {
            octets = fichier.getBytes();
        } catch (IOException e) {
            throw ApiException.requeteInvalide("Fichier illisible");
        }
        if (!ressembleAUnAudio(octets)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Ce fichier n'est pas reconnu comme un fichier audio");
        }
        try {
            Files.createDirectories(dossier);
            String base = nomSur(original.substring(0, original.length() - ext.length() - 1));
            Path cible = dossier.resolve(base + "." + ext);
            for (int i = 2; Files.exists(cible); i++) cible = dossier.resolve(base + " (" + i + ")." + ext);
            Files.write(cible, octets);
            return new Piste(id(cible.getFileName().toString()), nom(cible.getFileName().toString()), octets.length, cible);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Enregistrement impossible : " + e.getMessage());
        }
    }

    public void supprimer(String id) {
        Piste p = resoudre(id);
        try {
            Files.deleteIfExists(p.chemin());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Suppression impossible : " + e.getMessage());
        }
    }

    public static String typeMime(String nomFichier) {
        return switch (extension(nomFichier)) {
            case "mp3" -> "audio/mpeg";
            case "m4a" -> "audio/mp4";
            case "aac" -> "audio/aac";
            case "ogg" -> "audio/ogg";
            case "wav" -> "audio/wav";
            default -> "application/octet-stream";
        };
    }

    // ------------------------------------------------------------------ outils

    static String extension(String nom) {
        int i = nom.lastIndexOf('.');
        return i < 0 ? "" : nom.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    /** Titre affiché : nom du fichier sans extension, tirets bas remplacés par des espaces. */
    static String nom(String fichier) {
        int i = fichier.lastIndexOf('.');
        String base = i < 0 ? fichier : fichier.substring(0, i);
        return base.replace('_', ' ').replaceAll("\\s+", " ").trim();
    }

    /** Nom de fichier sans séparateur de dossier ni caractère douteux. */
    static String nomSur(String base) {
        String propre = base.replaceAll("[^\\p{L}\\p{N} _.()-]", "").replaceAll("\\.{2,}", ".").trim();
        propre = propre.replaceAll("^[.\\s]+", "");
        if (propre.length() > 80) propre = propre.substring(0, 80).trim();
        return propre.isEmpty() ? "piste" : propre;
    }

    static String id(String nomFichier) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(nomFichier.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h).substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** MP3 (ID3 ou trame), AAC/ADTS, MP4/M4A (ftyp), OGG, WAV : on vérifie l'en-tête, pas seulement l'extension. */
    static boolean ressembleAUnAudio(byte[] o) {
        if (o.length < 12) return false;
        String debut = new String(o, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
        if (debut.startsWith("ID3") || debut.equals("OggS")) return true;
        if (debut.equals("RIFF") && new String(o, 8, 4, java.nio.charset.StandardCharsets.ISO_8859_1).equals("WAVE")) return true;
        if (new String(o, 4, 4, java.nio.charset.StandardCharsets.ISO_8859_1).equals("ftyp")) return true;
        return (o[0] & 0xFF) == 0xFF && (o[1] & 0xE0) == 0xE0;
    }

    private static long taille(Path f) {
        try {
            return Files.size(f);
        } catch (IOException e) {
            return 0;
        }
    }
}
