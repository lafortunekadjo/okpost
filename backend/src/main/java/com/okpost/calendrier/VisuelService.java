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

    public VisuelService(VisuelGenereRepository visuels, CalendrierService calendriers, AbonnementService abonnements,
                         StockageFichiers stockage, List<GenerateurImage> generateurs, ClientService clients,
                         ClientAssetRepository assets, CompositionVisuel composition, MoteurAffiche affiches, BanqueImages banque,
                         com.okpost.config.OkpostProperties props) {
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
        v.setAssetId(deLaBanque == null ? null : deLaBanque.getId());
        v.setDiapositive(diapositive);
        return vue(visuels.save(v));
    }

    /** Offre de la fiche citée par le brief (nom exact, sans tenir compte de la casse ni des accents). */
    static Client.Offre produitMisEnAvant(Client client, Modeles.BriefVisuel brief) {
        if (brief == null || brief.produit() == null || brief.produit().isBlank() || client.getOffres() == null) return null;
        String voulu = normaliser(brief.produit());
        return client.getOffres().stream().filter(o -> o.nom() != null && normaliser(o.nom()).equals(voulu)).findFirst().orElse(null);
    }

    private static String normaliser(String t) {
        return java.text.Normalizer.normalize(t.trim(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
    }

    /** Pour un produit : le prix et le nom viennent de la fiche (jamais inventés) et le gabarit « fiche produit » est utilisé par défaut. */
    static MoteurAffiche.Contenu avecProduit(MoteurAffiche.Contenu c, Client.Offre produit) {
        String prix = c.chiffre() != null && !c.chiffre().isBlank() ? c.chiffre() : produit.prix();
        String titre = c.titre() != null && !c.titre().isBlank() ? c.titre() : produit.nom();
        MoteurAffiche.Gabarit g = c.gabarit() == MoteurAffiche.Gabarit.PLEIN_CADRE || c.gabarit() == null ? MoteurAffiche.Gabarit.PRODUIT : c.gabarit();
        return new MoteurAffiche.Contenu(g, titre, prix, c.texte(), c.cta());
    }

    /** Photo du produit dans la banque : celles rattachées à l'offre, en variant d'une publication à l'autre. */
    private java.util.Optional<ClientAsset> photoProduit(Client client, Client.Offre produit, UUID publicationId) {
        if (produit.photos() == null || produit.photos().isEmpty()) return java.util.Optional.empty();
        List<ClientAsset> photos = produit.photos().stream()
                .map(id -> assets.findByIdAndClientId(id, client.getId()).orElse(null))
                .filter(a -> a != null && a.getClientId().equals(client.getId()) && a.getMime() != null && a.getMime().startsWith("image/") && !a.getMime().contains("svg"))
                .toList();
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
    static final long MAX_VIDEO = 200L * 1024 * 1024;

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
            if (fichier.getSize() > MAX_VIDEO) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Vidéo trop lourde (200 Mo maximum)");
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
