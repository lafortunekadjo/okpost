package com.okpost.client;

import com.okpost.client.CatalogueCsv.Ligne;
import com.okpost.commun.ApiException;
import com.okpost.securite.Session;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Import d'un catalogue de produits depuis un tableur (CSV). Rien n'est enregistré : l'utilisateur relit puis enregistre la fiche. */
@Service
public class CatalogueService {

    static final long TAILLE_MAX = 1024 * 1024;

    /** produits : offres prêtes à fusionner dans la fiche ; photosRattachees : photos de la banque retrouvées par leur nom de fichier. */
    public record ResultatImport(List<Client.Offre> produits, List<String> avertissements, int photosRattachees) {}

    private final ClientService clients;
    private final ClientAssetRepository assets;

    public CatalogueService(ClientService clients, ClientAssetRepository assets) {
        this.clients = clients;
        this.assets = assets;
    }

    @Transactional(readOnly = true)
    public ResultatImport importer(Session s, UUID clientId, MultipartFile fichier) {
        clients.charger(s, clientId);
        if (fichier == null || fichier.isEmpty()) throw ApiException.requeteInvalide("Aucun fichier reçu");
        if (fichier.getSize() > TAILLE_MAX) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Fichier trop lourd (1 Mo maximum)");
        byte[] octets;
        try {
            octets = fichier.getBytes();
        } catch (IOException e) {
            throw ApiException.requeteInvalide("Fichier illisible");
        }
        List<ClientAsset> photos = assets.findByClientIdOrderByCreatedAt(clientId).stream()
                .filter(a -> a.getType() == ClientAsset.Type.PHOTO && a.getNomOrigine() != null).toList();
        return convertir(CatalogueCsv.lire(octets), photos);
    }

    /** Sépare du service la logique pure (testable sans base de données). */
    static ResultatImport convertir(CatalogueCsv.Lecture lecture, List<ClientAsset> photosBanque) {
        Map<String, UUID> parNom = new HashMap<>();
        for (ClientAsset a : photosBanque) {
            String nom = a.getNomOrigine().trim().toLowerCase(Locale.ROOT);
            parNom.putIfAbsent(nom, a.getId());
            int point = nom.lastIndexOf('.');
            if (point > 0) parNom.putIfAbsent(nom.substring(0, point), a.getId());
        }
        List<String> avertissements = new ArrayList<>(lecture.avertissements());
        List<String> introuvables = new ArrayList<>();
        List<Client.Offre> produits = new ArrayList<>();
        int rattachees = 0;
        for (Ligne l : lecture.lignes()) {
            List<UUID> ids = new ArrayList<>();
            for (String nomPhoto : l.photos()) {
                UUID id = parNom.get(nomPhoto.trim().toLowerCase(Locale.ROOT));
                if (id == null) {
                    if (introuvables.size() < 10 && !introuvables.contains(nomPhoto)) introuvables.add(nomPhoto);
                } else if (!ids.contains(id) && ids.size() < 10) {
                    ids.add(id);
                    rattachees++;
                }
            }
            produits.add(new Client.Offre(l.nom(), l.description(), l.prix(), null, ids.isEmpty() ? null : ids,
                    l.categorie(), l.tailles().isEmpty() ? null : l.tailles(), l.couleurs().isEmpty() ? null : l.couleurs(), l.disponible()));
        }
        if (!introuvables.isEmpty()) {
            avertissements.add("Photos introuvables dans la banque d'images : " + String.join(", ", introuvables)
                    + ". Ajoute-les d'abord à la banque (même nom de fichier) puis réimporte.");
        }
        return new ResultatImport(produits, avertissements, rattachees);
    }
}
