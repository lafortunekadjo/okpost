package com.okpost.client;

import com.okpost.abonnement.AbonnementService;
import com.okpost.client.ClientDtos.AssetVue;
import com.okpost.client.ClientDtos.ClientForm;
import com.okpost.client.ClientDtos.ClientVue;
import com.okpost.commun.ApiException;
import com.okpost.securite.Role;
import com.okpost.securite.Session;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
public class ClientService {

    private final ClientRepository clients;
    private final ClientAssetRepository assets;
    private final StockageFichiers stockage;
    private final AbonnementService abonnements;

    public ClientService(ClientRepository clients, ClientAssetRepository assets, StockageFichiers stockage,
                         AbonnementService abonnements) {
        this.clients = clients;
        this.assets = assets;
        this.stockage = stockage;
        this.abonnements = abonnements;
    }

    @Transactional(readOnly = true)
    public List<Client> lister(Session s) {
        return s.role() == Role.CM
                ? clients.findByWorkspaceIdAndCmIdAndArchiveFalseOrderByNom(s.workspaceId(), s.userId())
                : clients.findByWorkspaceIdAndArchiveFalseOrderByNom(s.workspaceId());
    }

    /** Charge un client en vérifiant qu'il appartient à l'espace (et au CM s'il n'est pas admin). */
    @Transactional(readOnly = true)
    public Client charger(Session s, UUID id) {
        Client c = clients.findByIdAndWorkspaceId(id, s.workspaceId())
                .orElseThrow(() -> ApiException.introuvable("Client"));
        if (s.role() == Role.CM && c.getCmId() != null && !c.getCmId().equals(s.userId())) {
            throw ApiException.introuvable("Client");
        }
        return c;
    }

    @Transactional
    public Client creer(Session s, ClientForm f) {
        abonnements.exigerEcriture(s.workspaceId());
        int max = abonnements.formule(s.workspaceId()).maxClients();
        if (clients.countByWorkspaceIdAndArchiveFalse(s.workspaceId()) >= max) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED,
                    "Ta formule permet " + max + " client(s). Passe à la formule supérieure pour en ajouter.");
        }
        Client c = new Client();
        c.setWorkspaceId(s.workspaceId());
        c.setCmId(s.role() == Role.CM ? s.userId() : (f.cmId() != null ? f.cmId() : s.userId()));
        appliquer(c, f);
        return clients.save(c);
    }

    @Transactional
    public Client modifier(Session s, UUID id, ClientForm f) {
        abonnements.exigerEcriture(s.workspaceId());
        Client c = charger(s, id);
        appliquer(c, f);
        if (s.estAdmin() && f.cmId() != null) {
            c.setCmId(f.cmId());
        }
        return clients.save(c);
    }

    @Transactional
    public void archiver(Session s, UUID id) {
        Client c = charger(s, id);
        c.setArchive(true);
        clients.save(c);
    }

    @Transactional
    public ClientAsset ajouterFichier(Session s, UUID clientId, ClientAsset.Type type, String legende, MultipartFile fichier) {
        abonnements.exigerEcriture(s.workspaceId());
        charger(s, clientId);
        if (fichier.isEmpty()) {
            throw ApiException.requeteInvalide("Fichier vide");
        }
        byte[] octets;
        try {
            octets = fichier.getBytes();
        } catch (IOException e) {
            throw ApiException.requeteInvalide("Fichier illisible");
        }
        TypeImage t = TypeImage.detecter(octets);
        if (t == null) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formats acceptés : PNG, JPG, WebP ou SVG");
        }
        String chemin = stockage.enregistrer(new ByteArrayInputStream(octets), t.extension);
        ClientAsset a = new ClientAsset();
        a.setClientId(clientId);
        a.setType(type);
        a.setChemin(chemin);
        a.setNomOrigine(fichier.getOriginalFilename());
        a.setMime(t.mime);
        a.setTaille(octets.length);
        a.setLegende(legende);
        return assets.save(a);
    }

    @Transactional
    public void supprimerFichier(Session s, UUID clientId, UUID assetId) {
        charger(s, clientId);
        ClientAsset a = assets.findByIdAndClientId(assetId, clientId)
                .orElseThrow(() -> ApiException.introuvable("Fichier"));
        assets.delete(a);
        stockage.supprimer(a.getChemin());
    }

    @Transactional(readOnly = true)
    public ClientVue vue(Client c) {
        List<AssetVue> fichiers = assets.findByClientIdOrderByCreatedAt(c.getId()).stream()
                .map(a -> new AssetVue(a.getId(), a.getType().name(), ClientDtos.urlFichier(a.getChemin()),
                        a.getNomOrigine(), a.getLegende()))
                .toList();
        return new ClientVue(c.getId(), c.getNom(), c.getSecteur(), c.getDescription(), c.getSlogan(),
                c.getAnneeCreation(), c.getVille(), c.getPays(), c.getZone(), c.getAdresse(), c.getHoraires(),
                c.getLangue(), c.getTon(), c.isTutoiement(), c.getMotsAEviter(), c.getExemples(), c.getOffres(),
                c.getCibles(), c.getConcurrents(), c.getCouleurs(), c.getPolices(), c.getDomaine(), c.getReseaux(),
                c.getWhatsapp(), c.getCmId(), fichiers, c.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public String logoUrl(UUID clientId) {
        return assets.findByClientIdOrderByCreatedAt(clientId).stream()
                .filter(a -> a.getType() == ClientAsset.Type.LOGO_COULEUR || a.getType() == ClientAsset.Type.ICONE)
                .findFirst()
                .map(a -> ClientDtos.urlFichier(a.getChemin()))
                .orElse(null);
    }

    private static void appliquer(Client c, ClientForm f) {
        c.setNom(f.nom().trim());
        c.setSecteur(f.secteur());
        c.setDescription(f.description());
        c.setSlogan(f.slogan());
        c.setAnneeCreation(f.anneeCreation());
        c.setVille(f.ville());
        c.setPays(f.pays());
        c.setZone(f.zone());
        c.setAdresse(f.adresse());
        c.setHoraires(f.horaires());
        c.setLangue(f.langue() == null ? "fr" : f.langue());
        c.setTon(f.ton());
        c.setTutoiement(Boolean.TRUE.equals(f.tutoiement()));
        c.setMotsAEviter(f.motsAEviter());
        c.setExemples(f.exemples());
        c.setOffres(f.offres() == null ? new ArrayList<>() : new ArrayList<>(f.offres()));
        c.setCibles(f.cibles() == null ? new ArrayList<>() : new ArrayList<>(f.cibles()));
        c.setConcurrents(f.concurrents() == null ? new ArrayList<>() : new ArrayList<>(f.concurrents()));
        c.setCouleurs(f.couleurs() == null ? new ArrayList<>() : new ArrayList<>(f.couleurs()));
        c.setPolices(f.polices());
        c.setDomaine(f.domaine());
        c.setReseaux(f.reseaux() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(f.reseaux()));
        c.setWhatsapp(f.whatsapp());
    }

    /** Détection du format par les premiers octets, pas par l'extension déclarée. */
    enum TypeImage {
        PNG("png", "image/png"), JPG("jpg", "image/jpeg"), WEBP("webp", "image/webp"), SVG("svg", "image/svg+xml");

        final String extension;
        final String mime;

        TypeImage(String extension, String mime) {
            this.extension = extension;
            this.mime = mime;
        }

        static TypeImage detecter(byte[] o) {
            if (o.length > 8 && (o[0] & 0xFF) == 0x89 && o[1] == 'P' && o[2] == 'N' && o[3] == 'G') return PNG;
            if (o.length > 3 && (o[0] & 0xFF) == 0xFF && (o[1] & 0xFF) == 0xD8 && (o[2] & 0xFF) == 0xFF) return JPG;
            if (o.length > 12 && o[0] == 'R' && o[1] == 'I' && o[2] == 'F' && o[3] == 'F'
                    && o[8] == 'W' && o[9] == 'E' && o[10] == 'B' && o[11] == 'P') return WEBP;
            String debut = new String(o, 0, Math.min(o.length, 512), StandardCharsets.UTF_8).toLowerCase();
            if (debut.contains("<svg") && !debut.contains("<script")) return SVG;
            return null;
        }
    }
}
