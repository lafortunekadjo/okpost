package com.okpost.client;

import com.okpost.commun.ApiException;
import com.okpost.securite.Session;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Importe dans la fiche d'un client une image trouvée sur son site : logo, ou photo pour la banque d'images (décrite ensuite par l'IA). */
@Service
public class ImportImageService {

    private static final int IMPORTS_PAR_HEURE = 80;

    private final TelechargeurImage telechargeur;
    private final ClientService clients;
    private final AnalyseurPhoto analyseur;
    private final Map<UUID, ArrayDeque<Instant>> appels = new ConcurrentHashMap<>();

    public ImportImageService(TelechargeurImage telechargeur, ClientService clients, AnalyseurPhoto analyseur) {
        this.telechargeur = telechargeur;
        this.clients = clients;
        this.analyseur = analyseur;
    }

    public ClientAsset importer(Session s, UUID clientId, ClientAsset.Type type, String url) {
        if (type != ClientAsset.Type.PHOTO && type != ClientAsset.Type.LOGO_COULEUR) {
            throw ApiException.requeteInvalide("Seuls le logo et les photos peuvent être importés");
        }
        clients.charger(s, clientId); // accès vérifié avant tout téléchargement
        limiter(s.workspaceId());
        boolean logo = type == ClientAsset.Type.LOGO_COULEUR;
        NormalisationImage.Image image = NormalisationImage.normaliser(telechargeur.telecharger(url), logo);
        ClientAsset a = clients.ajouterOctets(s, clientId, type, null, image.octets(), nomDepuis(url, image.extension()));
        if (type == ClientAsset.Type.PHOTO) analyseur.analyser(a.getId());
        return a;
    }

    private void limiter(UUID workspaceId) {
        ArrayDeque<Instant> file = appels.computeIfAbsent(workspaceId, k -> new ArrayDeque<>());
        synchronized (file) {
            Instant limite = Instant.now().minus(Duration.ofHours(1));
            while (!file.isEmpty() && file.peekFirst().isBefore(limite)) file.pollFirst();
            if (file.size() >= IMPORTS_PAR_HEURE) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Trop d'imports d'images : réessaie dans quelques minutes");
            }
            file.addLast(Instant.now());
        }
    }

    /** Nom lisible tiré de l'adresse : « /uploads/equipe-2024.webp » devient « equipe-2024.jpg ». */
    static String nomDepuis(String url, String extension) {
        // Tolérant : une adresse avec des espaces ou des parenthèses ne doit pas empêcher de nommer le fichier
        String sansRequete = url == null ? "" : url.trim().replaceAll("[?#].*$", "");
        String base = sansRequete.substring(sansRequete.lastIndexOf('/') + 1);
        try {
            base = java.net.URLDecoder.decode(base, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // pourcentage mal formé : on garde le texte tel quel
        }
        base = base.replaceAll("\\.[A-Za-z0-9]{2,5}$", "").replaceAll("[^\\p{L}\\p{N}_-]", "-").replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        if (base.isEmpty()) base = "image-du-site";
        if (base.length() > 80) base = base.substring(0, 80);
        return base.toLowerCase(Locale.ROOT) + "." + extension;
    }
}
