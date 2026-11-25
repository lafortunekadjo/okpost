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

    private final com.okpost.admin.JournalService journal;
    private final AccesClientRepository acces;

    public ClientService(ClientRepository clients, ClientAssetRepository assets, StockageFichiers stockage,
                         AbonnementService abonnements, com.okpost.admin.JournalService journal, AccesClientRepository acces) {
        this.acces = acces;
        this.journal = journal;
        this.clients = clients;
        this.assets = assets;
        this.stockage = stockage;
        this.abonnements = abonnements;
    }

    @Transactional(readOnly = true)
    public List<Client> lister(Session s) {
        if (s.estCollaborateur()) {
            // Invité sur un seul client : la liste ne contient que lui, et seulement si l'accès existe encore
            return s.clientRestreint() == null ? List.of() : clients.findByIdAndWorkspaceId(s.clientRestreint(), s.workspaceId())
                    .filter(c -> !c.isArchive() && acces.existsByClientIdAndUserId(c.getId(), s.userId())).map(List::of).orElse(List.of());
        }
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
        // Une session limitée à un client ne peut charger que lui, tant que l'accès n'a pas été retiré et que le client n'est pas archivé
        if (s.estCollaborateur() && (!id.equals(s.clientRestreint()) || c.isArchive()
                || !acces.existsByClientIdAndUserId(id, s.userId()))) {
            throw ApiException.introuvable("Client");
        }
        return c;
    }

    @Transactional
    public Client creer(Session s, ClientForm f) {
        s.exigerProprietaire();
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
        Client cree = clients.save(c);
        journal.enregistrer(s, com.okpost.admin.Actions.CLIENT_CREE, cree.getNom(), null);
        return cree;
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
    public ClientAsset ajouterFichier(Session s, UUID clientId, ClientAsset.Type type, String legende, MultipartFile fichier) {
        abonnements.exigerEcriture(s.workspaceId());
        charger(s, clientId);
        if (fichier.isEmpty()) {
            throw ApiException.requeteInvalide("Fichier vide");
        }
        if (fichier.getSize() > 5L * 1024 * 1024) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Fichier trop lourd (5 Mo maximum)");
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

    /** Enregistre une image déjà téléchargée et préparée (import depuis le site web d'un client). */
    @Transactional
    public ClientAsset ajouterOctets(Session s, UUID clientId, ClientAsset.Type type, String legende, byte[] octets, String nomOrigine) {
        abonnements.exigerEcriture(s.workspaceId());
        charger(s, clientId);
        if (octets == null || octets.length == 0) throw ApiException.requeteInvalide("Fichier vide");
        if (octets.length > 5L * 1024 * 1024) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Fichier trop lourd (5 Mo maximum)");
        TypeImage t = TypeImage.detecter(octets);
        if (t == null || (t == TypeImage.SVG && type == ClientAsset.Type.PHOTO)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formats acceptés : PNG, JPG ou WebP");
        }
        String chemin = stockage.enregistrer(new ByteArrayInputStream(octets), t.extension);
        ClientAsset a = new ClientAsset();
        a.setClientId(clientId);
        a.setType(type);
        a.setChemin(chemin);
        a.setNomOrigine(nomOrigine);
        a.setMime(t.mime);
        a.setTaille(octets.length);
        a.setLegende(legende);
        return assets.save(a);
    }

    /** Corrige la description ou les mots-clés d'une photo (la recherche de la banque s'appuie dessus). */
    @Transactional
    public ClientAsset modifierFichier(Session s, UUID clientId, UUID assetId, ClientDtos.AssetModif m) {
        abonnements.exigerEcriture(s.workspaceId());
        charger(s, clientId);
        ClientAsset a = assets.findByIdAndClientId(assetId, clientId)
                .orElseThrow(() -> ApiException.introuvable("Fichier"));
        if (m.legende() != null) a.setLegende(m.legende().isBlank() ? null : m.legende().trim());
        if (m.description() != null) a.setDescription(m.description().isBlank() ? null : m.description().trim());
        if (m.tags() != null) a.setTags(AnalyseurPhoto.nettoyerTags(m.tags()));
        return assets.save(a);
    }

    @Transactional(readOnly = true)
    public ClientAsset photo(Session s, UUID clientId, UUID assetId) {
        charger(s, clientId);
        return assets.findByIdAndClientId(assetId, clientId).orElseThrow(() -> ApiException.introuvable("Fichier"));
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
                .map(AssetVue::de)
                .toList();
        return new ClientVue(c.getId(), c.getNom(), c.getSecteur(), c.getDescription(), c.getSlogan(),
                c.getAnneeCreation(), c.getVille(), c.getPays(), c.getZone(), c.getAdresse(), c.getHoraires(),
                c.getLangue(), c.getTon(), c.isTutoiement(), c.getMotsAEviter(), c.getExemples(), c.getOffres(),
                c.getCibles(), c.getConcurrents(), c.getCouleurs(), c.getPolices(), c.getDomaine(), c.getReseaux(),
                c.getWhatsapp(), c.getCmId(), fichiers, c.getUpdatedAt(), c.isAutoPublication(), c.getHeurePublication(),
                c.isFetesReligieuses(), c.getTypeActivite(), c.getPositionFiligrane(), c.getDevise());
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
        c.setOffres(nettoyerOffres(f.offres()));
        c.setCibles(f.cibles() == null ? new ArrayList<>() : new ArrayList<>(f.cibles()));
        c.setConcurrents(f.concurrents() == null ? new ArrayList<>() : new ArrayList<>(f.concurrents()));
        c.setCouleurs(f.couleurs() == null ? new ArrayList<>() : new ArrayList<>(f.couleurs()));
        c.setPolices(f.polices());
        c.setDomaine(f.domaine());
        c.setReseaux(f.reseaux() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(f.reseaux()));
        c.setWhatsapp(f.whatsapp());
        if (f.autoPublication() != null) c.setAutoPublication(f.autoPublication());
        if (f.heurePublication() != null) c.setHeurePublication(f.heurePublication());
        if (f.fetesReligieuses() != null) c.setFetesReligieuses(f.fetesReligieuses());
        if (f.typeActivite() != null) c.setTypeActivite(f.typeActivite());
        if (f.positionFiligrane() != null) c.setPositionFiligrane(f.positionFiligrane());
        if (f.devise() != null && !f.devise().isBlank()) c.setDevise(f.devise().trim());
    }

    static final int OFFRES_MAX = 300;

    /** Catalogue plafonné et nettoyé : les textes viennent d'un formulaire ou d'un import et finissent dans les prompts de l'IA. */
    static List<Client.Offre> nettoyerOffres(List<Client.Offre> offres) {
        List<Client.Offre> propres = new ArrayList<>();
        if (offres == null) return propres;
        for (Client.Offre o : offres) {
            if (o == null || o.nom() == null || o.nom().isBlank()) continue;
            if (propres.size() >= OFFRES_MAX) break;
            propres.add(new Client.Offre(texte(o.nom(), 150), texte(o.description(), 500), texte(o.prix(), 100), texte(o.variantes(), 255),
                    o.photos() == null || o.photos().isEmpty() ? null : o.photos().stream().filter(java.util.Objects::nonNull).distinct().limit(10).toList(),
                    texte(o.categorie(), 60), mots(o.tailles()), mots(o.couleurs()), o.disponible()));
        }
        return propres;
    }

    private static String texte(String t, int max) {
        if (t == null) return null;
        String v = t.trim().replaceAll("[\\p{Cntrl}&&[^\\n]]", "");
        if (v.isEmpty()) return null;
        return v.length() > max ? v.substring(0, max) : v;
    }

    private static List<String> mots(List<String> valeurs) {
        if (valeurs == null) return null;
        List<String> l = valeurs.stream().map(v -> texte(v, 20)).filter(java.util.Objects::nonNull).distinct().limit(20).toList();
        return l.isEmpty() ? null : l;
    }

    /** Détection du format par les premiers octets, pas par l'extension déclarée. */
    public enum TypeImage {
        PNG("png", "image/png"), JPG("jpg", "image/jpeg"), WEBP("webp", "image/webp"), SVG("svg", "image/svg+xml");

        public final String extension;
        public final String mime;

        TypeImage(String extension, String mime) {
            this.extension = extension;
            this.mime = mime;
        }

        public static TypeImage detecter(byte[] o) {
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
