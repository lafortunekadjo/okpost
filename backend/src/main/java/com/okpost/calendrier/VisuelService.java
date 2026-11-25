package com.okpost.calendrier;

import com.okpost.abonnement.AbonnementService;
import com.okpost.calendrier.CalendrierDtos.VisuelVue;
import com.okpost.client.Client;
import com.okpost.client.ClientAsset;
import com.okpost.client.ClientAssetRepository;
import com.okpost.client.ClientDtos;
import com.okpost.client.ClientService;
import com.okpost.client.StockageFichiers;
import com.okpost.commun.ApiException;
import com.okpost.ia.GenerateurImage;
import com.okpost.ia.GenerateurImage.ImageGeneree;
import com.okpost.ia.GenerationException;
import com.okpost.ia.Modeles;
import com.okpost.securite.Session;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VisuelService {

    private final VisuelGenereRepository visuels;
    private final CalendrierService calendriers;
    private final AbonnementService abonnements;
    private final StockageFichiers stockage;
    private final List<GenerateurImage> generateurs;
    private final ClientService clients;
    private final ClientAssetRepository assets;
    private final CompositionVisuel composition;
    private final MoteurAffiche affiches;
    private final BanqueImages banque;
    private final com.okpost.config.OkpostProperties props;
    private final PublicationRepository publications;

    public VisuelService(VisuelGenereRepository visuels, CalendrierService calendriers, AbonnementService abonnements,
                         StockageFichiers stockage, List<GenerateurImage> generateurs, ClientService clients,
                         ClientAssetRepository assets, CompositionVisuel composition, MoteurAffiche affiches, BanqueImages banque,
                         com.okpost.config.OkpostProperties props, PublicationRepository publications) {
        this.publications = publications;
        this.props = props;
        this.clients = clients;
        this.assets = assets;
        this.composition = composition;
        this.affiches = affiches;
        this.banque = banque;
        this.visuels = visuels;
        this.calendriers = calendriers;
        this.abonnements = abonnements;
        this.stockage = stockage;
        this.generateurs = generateurs;
    }

    /** Génère une image (appel synchrone, 10 à 40 secondes) et l'enregistre. */
    public VisuelVue generer(Session s, UUID publicationId, String promptDemande, String ratioDemande,
                             boolean habiller, String accrocheDemandee, String moteur) {
        return generer(s, publicationId, promptDemande, ratioDemande, habiller,
                new Saisie(accrocheDemandee, null, null, null, null, null), moteur, null);
    }

    /** diapositive : numéro de la diapositive d'un carrousel, dont le titre sert d'accroche et le texte précise la scène. */
    public VisuelVue generer(Session s, UUID publicationId, String promptDemande, String ratioDemande,
                             boolean habiller, Saisie saisie, String moteur, Integer diapositive) {
        abonnements.exigerEcriture(s.workspaceId());
        Publication p = calendriers.chargerPublication(s, publicationId);
        String prompt = promptDemande != null && !promptDemande.isBlank() ? promptDemande.trim()
                : p.getBrief() == null ? null : p.getBrief().promptImage();
        if (prompt == null || prompt.isBlank()) {
            throw ApiException.requeteInvalide("Pas de prompt d'image pour cette publication");
        }
        String scene = prompt;
        Modeles.TexteCarrousel diapo = null;
        if (diapositive != null) {
            List<Modeles.TexteCarrousel> textes = p.getBrief() == null ? null : p.getBrief().textesCarrousel();
            if (textes == null || diapositive > textes.size()) {
                throw ApiException.requeteInvalide("Diapositive " + diapositive + " introuvable : enregistre d'abord les textes du carrousel");
            }
            diapo = textes.get(diapositive - 1);
            prompt = promptDiapositive(prompt, diapo, diapositive, textes.size());
        }
        String ratio = ratioDemande != null ? ratioDemande
                : p.getBrief() != null && p.getBrief().ratio() != null ? p.getBrief().ratio() : "1:1";
        Calendrier cal = calendriers.charger(s, p.getCalendrierId());
        Client client = clients.charger(s, cal.getClientId());
        // L'affiche est composée par OkPost (MoteurAffiche) sur une photo : le titre, le chiffre, le bouton, le logo et
        // les coordonnées sont dessinés par OkPost, donc toujours exacts, lisibles et aux couleurs de la marque.
        MoteurAffiche.Contenu contenu = null;
        CompositionVisuel.Habillage marque = null;
        if (habiller) {
            Fiche f = fiche(client);
            marque = new CompositionVisuel.Habillage(f.logo(), f.couleur(), f.contacts());
            contenu = contenuAffiche(p.getBrief(), diapo, saisie);
        }
        if (contenu != null && contenu.gabarit() == MoteurAffiche.Gabarit.COLLECTION) {
            return collection(s, p, client, contenu, marque, ratio, scene);
        }
        // Photo de fond : la banque du client d'abord (vraies photos, gratuit, instantané), l'IA si rien ne correspond.
        String mode = saisie == null || saisie.photo() == null ? "AUTO" : saisie.photo().toUpperCase(java.util.Locale.ROOT);
        ClientAsset deLaBanque = null;
        Client.Offre produit = habiller ? produitMisEnAvant(client, p.getBrief()) : null;
        if (produit != null) {
            contenu = avecProduit(contenu, produit);
        }
        if (habiller && !"IA".equals(mode) && produit != null) {
            // Publication sur un produit précis : sa vraie photo d'abord, jamais l'image d'un autre article
            deLaBanque = photoProduit(client, produit, p.getId()).orElse(null);
            if (deLaBanque == null) deLaBanque = banque.choisir(client.getId(), produit.nom() + " " + (produit.description() == null ? "" : produit.description())).orElse(null);
            if (deLaBanque == null && (client.vendDesProduits() || "BANQUE".equals(mode))) {
                throw ApiException.requeteInvalide("Aucune photo pour « " + produit.nom() + " ». Ajoute-en une à ce produit (fiche client, onglet Offres) : "
                        + "une image générée par l'IA ne montrerait pas le vrai article. Tu peux aussi choisir la génération par IA pour un visuel d'ambiance.");
            }
        } else if (habiller && !"IA".equals(mode)) {
            String requete = String.join(" ", p.getIdee() == null ? "" : p.getIdee(),
                    p.getBrief() == null || p.getBrief().description() == null ? "" : p.getBrief().description(),
                    scene, contenu.titre() == null ? "" : contenu.titre(), contenu.texte() == null ? "" : contenu.texte());
            deLaBanque = banque.choisir(client.getId(), requete).orElse(null);
            if (deLaBanque == null && "BANQUE".equals(mode)) {
                throw ApiException.requeteInvalide("Aucune photo de la banque ne correspond à cette publication. "
                        + "Ajoute des photos à la fiche du client, ou choisis la génération par IA.");
            }
        }
        byte[] photo;
        String fournisseur;
        String modele;
        VisuelGenere.Source source;
        if (deLaBanque != null) {
            try (var in = stockage.lire(deLaBanque.getChemin()).getInputStream()) {
                photo = in.readAllBytes();
            } catch (java.io.IOException e) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Photo de la banque illisible");
            }
            fournisseur = "banque";
            modele = deLaBanque.getId().toString();
            source = VisuelGenere.Source.BANQUE;
        } else {
            int quota = abonnements.formule(s.workspaceId()).imagesParMois();
            long ceMois = visuels.countByWorkspaceIdAndSourceAndCreatedAtAfter(s.workspaceId(), VisuelGenere.Source.GENERE,
                    LocalDate.now().withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC));
            if (ceMois >= quota) {
                throw new ApiException(HttpStatus.PAYMENT_REQUIRED,
                        "Tu as utilisé tes " + quota + " images IA du mois. Passe à la formule supérieure pour en générer plus.");
            }
            GenerateurImage ia = choisir(moteur);
            org.slf4j.LoggerFactory.getLogger(VisuelService.class).info("Image : moteur demandé {}, réglage {}, utilisé {}",
                    moteur, props.ia().image() == null ? null : props.ia().image().fournisseur(), ia.nom());
            String promptEnvoye = contenu != null ? promptPhoto(prompt, contenu.gabarit()) : prompt;
            ImageGeneree image;
            try {
                image = ia.generer(s.workspaceId(), promptEnvoye, ratio, List.of());
            } catch (GenerationException e) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, e.getMessage());
            }
            photo = image.octets();
            fournisseur = ia.nom();
            modele = image.modele();
            source = VisuelGenere.Source.GENERE;
        }
        byte[] octets = contenu != null ? affiches.composer(photo, ratio, contenu, marque)
                : composition.recadrer(photo, ratio);
        return enregistrerVisuel(s, p, octets, prompt, ratio, fournisseur, modele, source,
                deLaBanque == null ? null : deLaBanque.getId(), diapositive);
    }

    /** Contrôle le format, enregistre le fichier et la ligne du visuel. */
    private VisuelVue enregistrerVisuel(Session s, Publication p, byte[] octets, String prompt, String ratio, String fournisseur,
                                        String modele, VisuelGenere.Source source, UUID assetId, Integer diapositive) {
        ClientService.TypeImage type = ClientService.TypeImage.detecter(octets);
        if (type == null || type == ClientService.TypeImage.SVG) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Image reçue dans un format inattendu");
        }
        String chemin = stockage.enregistrer(new ByteArrayInputStream(octets), type.extension);
        VisuelGenere v = new VisuelGenere();
        v.setWorkspaceId(s.workspaceId());
        v.setPublicationId(p.getId());
        v.setChemin(chemin);
        v.setMime(type.mime);
        v.setPrompt(prompt);
        v.setRatio(ratio);
        v.setFournisseur(fournisseur);
        v.setModele(modele);
        v.setSource(source);
        v.setAssetId(assetId);
        v.setDiapositive(diapositive);
        return vue(visuels.save(v));
    }

    static final String CARROUSEL_PRODUIT = "carrousel-produit";
    static final int DIAPOSITIVES_MAX = 10;

    /**
     * Carrousel des photos d'un produit : une diapositive par photo rattachée (10 au plus, limite des réseaux), chacune avec le logo en
     * filigrane et sans texte. Les diapositives créées par une précédente exécution sont remplacées ; celles ajoutées à la main restent.
     */
    @Transactional
    public void carrouselProduit(Session s, UUID publicationId) {
        abonnements.exigerEcriture(s.workspaceId());
        Publication p = calendriers.chargerPublication(s, publicationId);
        if (!"CARROUSEL".equals(p.getFormat())) {
            throw ApiException.requeteInvalide("Cette publication n'est pas un carrousel : passe son format en « Carrousel » d'abord.");
        }
        Client client = clients.charger(s, calendriers.charger(s, p.getCalendrierId()).getClientId());
        Client.Offre produit = produitMisEnAvant(client, p.getBrief());
        if (produit == null) {
            throw ApiException.requeteInvalide("Indique le produit mis en avant (champ « Produit », nom exact dans la fiche client) puis enregistre.");
        }
        List<ClientAsset> photos = photosDuProduit(client, produit).stream().limit(DIAPOSITIVES_MAX).toList();
        if (photos.size() < 2) {
            throw ApiException.requeteInvalide("Un carrousel demande au moins 2 photos rattachées au produit « " + produit.nom() + " » ("
                    + photos.size() + " trouvée(s)). Ajoute-les dans la fiche client, onglet Offres.");
        }
        String ratio = p.getBrief().ratio() != null && p.getBrief().ratio().matches("1:1|4:5|9:16") ? p.getBrief().ratio() : "4:5";
        Fiche f = fiche(client);
        CompositionVisuel.Habillage marque = new CompositionVisuel.Habillage(f.logo(), f.couleur(), List.of());
        MoteurAffiche.Contenu contenu = new MoteurAffiche.Contenu(MoteurAffiche.Gabarit.PHOTO_SEULE, null, null, null, null);

        List<VisuelGenere> anciens = visuels.findByPublicationIdInOrderByCreatedAtDesc(List.of(p.getId())).stream()
                .filter(v -> CARROUSEL_PRODUIT.equals(v.getFournisseur())).toList();
        List<String> aEffacer = anciens.stream().map(VisuelGenere::getChemin).toList();
        visuels.deleteAll(anciens);
        visuels.flush();

        List<Modeles.TexteCarrousel> textes = new java.util.ArrayList<>();
        for (int i = 0; i < photos.size(); i++) {
            byte[] source;
            try (var in = stockage.lire(photos.get(i).getChemin()).getInputStream()) {
                source = in.readAllBytes();
            } catch (java.io.IOException e) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Photo " + (i + 1) + " du produit illisible");
            }
            byte[] octets = affiches.composer(source, ratio, contenu, marque);
            enregistrerVisuel(s, p, octets, produit.nom(), ratio, CARROUSEL_PRODUIT, "photo", VisuelGenere.Source.BANQUE, photos.get(i).getId(), i + 1);
            textes.add(new Modeles.TexteCarrousel(i + 1, "Photo " + (i + 1), ""));
        }
        p.setBrief(avecDiapositives(p.getBrief(), ratio, textes));
        publications.save(p);
        com.okpost.client.FichiersApresCommit.supprimer(stockage, aEffacer);
    }

    static Modeles.BriefVisuel avecDiapositives(Modeles.BriefVisuel b, String ratio, List<Modeles.TexteCarrousel> textes) {
        return new Modeles.BriefVisuel(b.type() == null ? "CARROUSEL" : b.type(), b.description(), b.promptImage(), ratio, textes, b.accroche(),
                "PHOTO_SEULE", b.chiffre(), b.texte(), b.cta(), b.produit(), b.produits());
    }

    /** Collection : 2 à 4 produits de la fiche, chacun avec sa vraie photo, en grille. Aucune image IA, donc aucun quota consommé. */
    private VisuelVue collection(Session s, Publication p, Client client, MoteurAffiche.Contenu contenu,
                                 CompositionVisuel.Habillage marque, String ratio, String scene) {
        List<String> demandes = p.getBrief() == null || p.getBrief().produits() == null ? List.of() : p.getBrief().produits();
        List<MoteurAffiche.Article> articles = new java.util.ArrayList<>();
        java.util.Set<String> deja = new java.util.HashSet<>();
        UUID premiere = null;
        for (String nom : demandes) {
            Client.Offre o = offreNommee(client, nom);
            if (o == null || !deja.add(normaliser(o.nom()))) continue;
            ClientAsset photo = photoProduit(client, o, p.getId()).orElse(null);
            if (photo == null) continue;
            try (var in = stockage.lire(photo.getChemin()).getInputStream()) {
                articles.add(new MoteurAffiche.Article(in.readAllBytes(), o.nom(), o.prix()));
            } catch (java.io.IOException e) {
                continue;
            }
            if (premiere == null) premiere = photo.getId();
            if (articles.size() == 4) break;
        }
        if (articles.size() < 2) {
            throw ApiException.requeteInvalide("Une collection demande 2 à 4 produits de la fiche qui ont chacun une photo (" + articles.size()
                    + " trouvé(s)). Indique leurs noms exacts dans « Produits » et rattache une photo à chacun (fiche client, onglet Offres).");
        }
        byte[] octets = affiches.composerCollection(articles, ratio, contenu.titre(), contenu.cta(), marque);
        return enregistrerVisuel(s, p, octets, scene, ratio, "collection", "grille", VisuelGenere.Source.BANQUE, premiere, null);
    }

    /** Offre de la fiche citée par le brief (nom exact, sans tenir compte de la casse ni des accents). */
    static Client.Offre produitMisEnAvant(Client client, Modeles.BriefVisuel brief) {
        return brief == null ? null : offreNommee(client, brief.produit());
    }

    static Client.Offre offreNommee(Client client, String nom) {
        if (nom == null || nom.isBlank() || client.getOffres() == null) return null;
        String voulu = normaliser(nom);
        return client.getOffres().stream().filter(o -> o.nom() != null && normaliser(o.nom()).equals(voulu)).findFirst().orElse(null);
    }

    private static String normaliser(String t) {
        return java.text.Normalizer.normalize(t.trim(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
    }

    /** Pour un produit : le prix et le nom viennent de la fiche (jamais inventés) et le gabarit « fiche produit » est utilisé par défaut. */
    static MoteurAffiche.Contenu avecProduit(MoteurAffiche.Contenu c, Client.Offre produit) {
        String titre = c.titre() != null && !c.titre().isBlank() ? c.titre() : produit.nom();
        MoteurAffiche.Gabarit g = c.gabarit() == MoteurAffiche.Gabarit.PLEIN_CADRE || c.gabarit() == null ? MoteurAffiche.Gabarit.PRODUIT : c.gabarit();
        boolean prixSaisi = g == MoteurAffiche.Gabarit.PRODUIT && c.chiffre() != null && !c.chiffre().isBlank();
        String prix = prixSaisi ? c.chiffre() : produit.prix();
        // Pour NOUVEAUTE et PROMO, « chiffre » reste l'étiquette ou la remise ; pour PRODUIT il ne sert que de prix
        return new MoteurAffiche.Contenu(g, titre, g == MoteurAffiche.Gabarit.PRODUIT ? null : c.chiffre(), c.texte(), c.cta(), prix);
    }

    /** Photo du produit dans la banque : celles rattachées à l'offre, en variant d'une publication à l'autre. */
    /** Photos rattachées au produit, dans l'ordre choisi (images de ce client uniquement, ni logo vectoriel ni fichier supprimé). */
    private List<ClientAsset> photosDuProduit(Client client, Client.Offre produit) {
        if (produit.photos() == null || produit.photos().isEmpty()) return List.of();
        return produit.photos().stream()
                .map(id -> assets.findByIdAndClientId(id, client.getId()).orElse(null))
                .filter(a -> a != null && a.getMime() != null && a.getMime().startsWith("image/") && !a.getMime().contains("svg"))
                .toList();
    }

    private java.util.Optional<ClientAsset> photoProduit(Client client, Client.Offre produit, UUID publicationId) {
        List<ClientAsset> photos = photosDuProduit(client, produit);
        if (photos.isEmpty()) return java.util.Optional.empty();
        java.util.Set<UUID> deja = visuels.findByPublicationIdInOrderByCreatedAtDesc(List.of(publicationId)).stream()
                .map(VisuelGenere::getAssetId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        return photos.stream().filter(a -> !deja.contains(a.getId())).findFirst().or(() -> java.util.Optional.of(photos.get(0)));
    }

    /** Scène d'une diapositive : même univers visuel que le carrousel, contenu propre à la diapositive. */
    static String promptDiapositive(String scene, Modeles.TexteCarrousel d, int numero, int total) {
        String base = scene.replaceAll("(?i)[,.;]?\\s*no text.*$", "").trim();
        StringBuilder sb = new StringBuilder(base);
        sb.append(". This image is slide ").append(numero).append(" of a ").append(total)
          .append("-slide carousel: keep exactly the same visual style, colour palette, lighting and setting as the other slides");
        if (d.texte() != null && !d.texte().isBlank()) {
            sb.append(", and illustrate this specific point of the slide (written in French, do not copy this sentence onto the image): \"")
              .append(d.texte().trim()).append('"');
        }
        sb.append('.');
        if (numero == total) sb.append(" As the last slide, it can feel like a conclusion or a call to action.");
        return sb.toString();
    }

    /** Moteur demandé s'il est configuré, sinon celui des réglages (okpost.ia.image.fournisseur), sinon le premier disponible. */
    private GenerateurImage choisir(String moteur) {
        String parDefaut = props.ia().image() == null ? null : props.ia().image().fournisseur();
        for (String nom : java.util.Arrays.asList(moteur, parDefaut)) {
            if (nom == null) continue;
            for (GenerateurImage g : generateurs) {
                if (g.nom().equals(nom.trim()) && g.configure()) return g;
            }
            if (nom.equals(moteur)) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Le moteur d'images « " + moteur + " » n'est pas configuré");
            }
        }
        return generateurs.stream().filter(GenerateurImage::configure).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "La génération d'images n'est pas configurée sur le serveur"));
    }

    /** Moteurs d'images et leur état (clé présente ou non). */
    public Map<String, Boolean> moteurs() {
        Map<String, Boolean> m = new LinkedHashMap<>();
        generateurs.forEach(g -> m.put(g.nom(), g.configure()));
        return m;
    }

    record Fiche(byte[] logo, String logoMime, String couleur, List<CompositionVisuel.Contact> contacts) {}

    /** Ce que le CM a saisi dans le formulaire : prioritaire sur le brief enregistré (champs vides = on garde le brief). */
    public record Saisie(String titre, String gabarit, String chiffre, String texte, String cta, String photo) {}

    private static String ou(String saisi, String brief) {
        return saisi != null && !saisi.isBlank() ? saisi : brief;
    }

    /** Ce qui s'écrit sur l'affiche : le brief de l'IA, avec la priorité à ce que le CM a saisi. */
    static MoteurAffiche.Contenu contenuAffiche(Modeles.BriefVisuel b, Modeles.TexteCarrousel diapo, Saisie saisie) {
        Saisie s = saisie == null ? new Saisie(null, null, null, null, null, null) : saisie;
        if (diapo != null) {
            // Diapositive de carrousel : photo pleine page, titre et texte de la diapositive.
            return new MoteurAffiche.Contenu(MoteurAffiche.Gabarit.PLEIN_CADRE, ou(s.titre(), diapo.titre()), null, diapo.texte(), null);
        }
        String titreBrief = b == null ? null
                : b.accroche() != null && !b.accroche().isBlank() ? b.accroche()
                : b.textesCarrousel() != null && !b.textesCarrousel().isEmpty() ? b.textesCarrousel().get(0).titre() : null;
        String chiffre = ou(s.chiffre(), b == null ? null : b.chiffre());
        String texte = ou(s.texte(), b == null ? null : b.texte());
        String cta = ou(s.cta(), b == null ? null : b.cta());
        MoteurAffiche.Gabarit g = MoteurAffiche.resoudre(s.gabarit(), b == null ? null : b.gabarit(), chiffre, texte);
        return new MoteurAffiche.Contenu(g, ou(s.titre(), titreBrief), chiffre, texte, cta);
    }

    /** Demande à l'IA une photo seule, cadrée pour laisser de la place au texte du gabarit. */
    static String promptPhoto(String scene, MoteurAffiche.Gabarit gabarit) {
        String visuel = scene.replaceAll("(?i)[,.;]?\\s*no text.*$", "").trim();
        return visuel + ". Composition: " + gabarit.cadrage + ". Photorealistic documentary photograph, natural light, "
                + "authentic candid expressions, sharp focus, realistic skin and fabric textures. "
                + "No text, no letters, no numbers, no logo, no watermark, no frame, no graphic design, no overlay.";
    }

    /** Logo, couleur et coordonnées tirés de la fiche client. */
    private Fiche fiche(Client c) {
        List<ClientAsset> fichiers = assets.findByClientIdOrderByCreatedAt(c.getId()).stream()
                .filter(a -> !a.getMime().contains("svg")).toList();
        ClientAsset logo = null;
        for (ClientAsset.Type t : List.of(ClientAsset.Type.LOGO_COULEUR, ClientAsset.Type.ICONE, ClientAsset.Type.LOGO_NOIR)) {
            logo = fichiers.stream().filter(a -> a.getType() == t).findFirst().orElse(null);
            if (logo != null) break;
        }
        if (logo == null) {
            logo = fichiers.stream().filter(a -> a.getType() == ClientAsset.Type.LOGO_BLANC).findFirst().orElse(null);
        }
        byte[] octetsLogo = null;
        if (logo != null) {
            try (var in = stockage.lire(logo.getChemin()).getInputStream()) {
                octetsLogo = in.readAllBytes();
            } catch (java.io.IOException e) {
                octetsLogo = null;
            }
        }
        List<CompositionVisuel.Contact> contacts = new java.util.ArrayList<>();
        if (c.getWhatsapp() != null && !c.getWhatsapp().isBlank()) {
            contacts.add(new CompositionVisuel.Contact(CompositionVisuel.TypeContact.WHATSAPP, null, c.getWhatsapp().trim()));
        }
        String site = c.getDomaine() == null ? "" : c.getDomaine().trim().replaceFirst("^https?://", "")
                .replaceFirst("^www\\.", "").replaceFirst("/$", "");
        if (!site.isBlank()) {
            contacts.add(new CompositionVisuel.Contact(CompositionVisuel.TypeContact.SITE, null, site));
        }
        contacts.addAll(reseaux(c.getReseaux()));
        String couleur = c.getCouleurs() == null || c.getCouleurs().isEmpty() ? "#111827" : c.getCouleurs().get(0);
        return new Fiche(octetsLogo, logo == null ? null : logo.getMime(), couleur, contacts.subList(0, Math.min(contacts.size(), 5)));
    }

    /** Un élément par pseudo : « TikTok · Instagram  @my2_zero » quand plusieurs réseaux partagent le même. */
    static List<CompositionVisuel.Contact> reseaux(Map<String, String> liens) {
        if (liens == null || liens.isEmpty()) return List.of();
        Map<String, List<String>> parPseudo = new LinkedHashMap<>();
        liens.forEach((reseau, url) -> {
            if (url == null || url.isBlank()) return;
            String propre = url.trim().replaceFirst("[?#].*$", "").replaceFirst("/+$", "");
            // Liens de partage ou de publication : ils ne contiennent pas le nom de la page.
            if (propre.matches("(?i).*/(share|p|posts|reel|videos|groups|permalink|watch)/.*") || propre.contains("profile.php")) {
                return;
            }
            String[] morceaux = propre.split("/");
            String pseudo = morceaux[morceaux.length - 1].replace("@", "");
            if (pseudo.isBlank() || pseudo.contains(".")) return;
            String nom = switch (reseau.toUpperCase(java.util.Locale.ROOT)) {
                case "TIKTOK" -> "TikTok";
                case "LINKEDIN" -> "LinkedIn";
                case "INSTAGRAM" -> "Instagram";
                case "FACEBOOK" -> "Facebook";
                default -> reseau;
            };
            parPseudo.computeIfAbsent(pseudo, k -> new java.util.ArrayList<>()).add(nom);
        });
        return parPseudo.entrySet().stream()
                .map(e -> new CompositionVisuel.Contact(CompositionVisuel.TypeContact.RESEAU,
                        String.join(" · ", e.getValue()), "@" + e.getKey()))
                .limit(3).toList();
    }

    static final long MAX_IMAGE = 10L * 1024 * 1024;
    static final long MAX_VIDEO = 25L * 1024 * 1024;

    /** Photo ou vidéo fournie par le CM (tournée, retouchée, faite sur Canva…). Le format est vérifié par les octets. */
    public VisuelVue televerser(Session s, UUID publicationId, org.springframework.web.multipart.MultipartFile fichier) {
        return televerser(s, publicationId, fichier, null);
    }

    public VisuelVue televerser(Session s, UUID publicationId, org.springframework.web.multipart.MultipartFile fichier,
                                Integer diapositive) {
        abonnements.exigerEcriture(s.workspaceId());
        Publication p = calendriers.chargerPublication(s, publicationId);
        if (fichier == null || fichier.isEmpty()) {
            throw ApiException.requeteInvalide("Fichier vide");
        }
        byte[] entete;
        try (var in = fichier.getInputStream()) {
            entete = in.readNBytes(64);
        } catch (java.io.IOException e) {
            throw ApiException.requeteInvalide("Fichier illisible");
        }
        String extension;
        String mime;
        VisuelGenere.TypeMedia typeMedia;
        ClientService.TypeImage image = ClientService.TypeImage.detecter(entete.length > 12 ? entete : new byte[0]);
        String video = typeVideo(entete);
        if (image != null && image != ClientService.TypeImage.SVG) {
            if (fichier.getSize() > MAX_IMAGE) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Photo trop lourde (10 Mo maximum)");
            extension = image.extension;
            mime = image.mime;
            typeMedia = VisuelGenere.TypeMedia.IMAGE;
        } else if (video != null) {
            if (fichier.getSize() > MAX_VIDEO) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Vidéo trop lourde (25 Mo maximum)");
            extension = video;
            mime = switch (video) { case "webm" -> "video/webm"; case "mov" -> "video/quicktime"; default -> "video/mp4"; };
            typeMedia = VisuelGenere.TypeMedia.VIDEO;
        } else {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formats acceptés : photo JPG, PNG ou WebP ; vidéo MP4, MOV ou WebM");
        }
        String chemin;
        try (var in = fichier.getInputStream()) {
            chemin = stockage.enregistrer(in, extension);
        } catch (java.io.IOException e) {
            throw ApiException.requeteInvalide("Fichier illisible");
        }
        VisuelGenere v = new VisuelGenere();
        v.setWorkspaceId(s.workspaceId());
        v.setPublicationId(p.getId());
        v.setChemin(chemin);
        v.setMime(mime);
        v.setSource(VisuelGenere.Source.TELEVERSE);
        v.setTypeMedia(typeMedia);
        v.setNomOrigine(fichier.getOriginalFilename());
        v.setTaille(fichier.getSize());
        if (diapositive != null && typeMedia == VisuelGenere.TypeMedia.IMAGE) v.setDiapositive(diapositive);
        return vue(visuels.save(v));
    }

    /** mp4 / mov (boîte « ftyp ») ou webm (EBML). */
    static String typeVideo(byte[] o) {
        if (o.length >= 12 && o[4] == 'f' && o[5] == 't' && o[6] == 'y' && o[7] == 'p') {
            String marque = new String(o, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
            return marque.startsWith("qt") ? "mov" : "mp4";
        }
        if (o.length >= 4 && (o[0] & 0xFF) == 0x1A && (o[1] & 0xFF) == 0x45 && (o[2] & 0xFF) == 0xDF && (o[3] & 0xFF) == 0xA3) {
            return "webm";
        }
        return null;
    }

    @Transactional
    public void supprimer(Session s, UUID publicationId, UUID visuelId) {
        calendriers.chargerPublication(s, publicationId);
        VisuelGenere v = visuels.findByIdAndPublicationId(visuelId, publicationId)
                .orElseThrow(() -> ApiException.introuvable("Image"));
        visuels.delete(v);
        stockage.supprimer(v.getChemin());
    }

    /** Images par publication, la plus récente en premier. */
    @Transactional(readOnly = true)
    public Map<UUID, List<VisuelVue>> parPublication(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return visuels.findByPublicationIdInOrderByCreatedAtDesc(ids).stream()
                .collect(Collectors.groupingBy(VisuelGenere::getPublicationId, LinkedHashMap::new,
                        Collectors.mapping(VisuelService::vue, Collectors.toList())));
    }

    static VisuelVue vue(VisuelGenere v) {
        return new VisuelVue(v.getId(), ClientDtos.urlFichier(v.getChemin()), v.getPrompt(), v.getRatio(),
                v.getModele(), v.getCreatedAt(), v.getTypeMedia().name(), v.getSource().name(), v.getNomOrigine(), v.getDiapositive());
    }
}
