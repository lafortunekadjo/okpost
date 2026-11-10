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
    private final com.okpost.config.OkpostProperties props;

    public VisuelService(VisuelGenereRepository visuels, CalendrierService calendriers, AbonnementService abonnements,
                         StockageFichiers stockage, List<GenerateurImage> generateurs, ClientService clients,
                         ClientAssetRepository assets, CompositionVisuel composition,
                         com.okpost.config.OkpostProperties props) {
        this.props = props;
        this.clients = clients;
        this.assets = assets;
        this.composition = composition;
        this.visuels = visuels;
        this.calendriers = calendriers;
        this.abonnements = abonnements;
        this.stockage = stockage;
        this.generateurs = generateurs;
    }

    /** Génère une image (appel synchrone, 10 à 40 secondes) et l'enregistre. */
    public VisuelVue generer(Session s, UUID publicationId, String promptDemande, String ratioDemande,
                             boolean habiller, String accrocheDemandee, String moteur) {
        return generer(s, publicationId, promptDemande, ratioDemande, habiller, accrocheDemandee, moteur, null);
    }

    /** diapositive : numéro de la diapositive d'un carrousel, dont le titre sert d'accroche et le texte précise la scène. */
    public VisuelVue generer(Session s, UUID publicationId, String promptDemande, String ratioDemande,
                             boolean habiller, String accrocheDemandee, String moteur, Integer diapositive) {
        abonnements.exigerEcriture(s.workspaceId());
        Publication p = calendriers.chargerPublication(s, publicationId);
        String prompt = promptDemande != null && !promptDemande.isBlank() ? promptDemande.trim()
                : p.getBrief() == null ? null : p.getBrief().promptImage();
        if (prompt == null || prompt.isBlank()) {
            throw ApiException.requeteInvalide("Pas de prompt d'image pour cette publication");
        }
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
        // Affiche complète : l'IA compose le visuel et l'accroche en laissant libres le coin du logo et le bas ;
        // OkPost pose ensuite le vrai logo et le bandeau de coordonnées, toujours exacts.
        // (Le logo n'est pas joint à l'IA : avec une image jointe, le modèle la retouche au lieu de créer une affiche.)
        String promptEnvoye = prompt;
        List<GenerateurImage.Reference> references = List.of();
        CompositionVisuel.Habillage bandeau = null;
        if (habiller) {
            Calendrier cal = calendriers.charger(s, p.getCalendrierId());
            Client client = clients.charger(s, cal.getClientId());
            String accroche = accrocheDemandee != null && !accrocheDemandee.isBlank() ? accrocheDemandee
                    : diapo != null ? diapo.titre()
                    : p.getBrief() == null ? null
                    : p.getBrief().accroche() != null ? p.getBrief().accroche()
                    : p.getBrief().textesCarrousel() != null && !p.getBrief().textesCarrousel().isEmpty()
                    ? p.getBrief().textesCarrousel().get(0).titre() : null;
            Fiche f = fiche(client);
            bandeau = new CompositionVisuel.Habillage(f.logo(), f.couleur(), f.contacts());
            promptEnvoye = promptAffiche(prompt, accroche, client.getNom(), client.getCouleurs(), f.logo() != null,
                    partBandeau(ratio, f.contacts().size()), f.couleur());
        }
        ImageGeneree image;
        try {
            image = ia.generer(s.workspaceId(), promptEnvoye, ratio, references);
        } catch (GenerationException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
        byte[] octets = composition.recadrer(image.octets(), ratio);
        if (bandeau != null && (!bandeau.contacts().isEmpty() || bandeau.logo() != null)) {
            octets = composition.habiller(octets, bandeau);
        }
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
        v.setFournisseur(ia.nom());
        v.setModele(image.modele());
        v.setDiapositive(diapositive);
        return vue(visuels.save(v));
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

    /** Consignes d'affiche envoyées au générateur d'images (en anglais, plus fiable). */
    static String promptAffiche(String scene, String accroche, String marque, List<String> couleurs, boolean logo,
                                int pourcentBandeau, String couleurBandeau) {
        String visuel = scene.replaceAll("(?i)[,.;]?\\s*no text.*$", "").trim();
        StringBuilder sb = new StringBuilder();
        sb.append("Professional social media poster for the brand \"").append(marque)
          .append("\", polished graphic design worthy of a top advertising agency, ready to publish.\n\n");
        sb.append("Visual: ").append(visuel).append("\n\n");
        if (accroche != null && !accroche.isBlank()) {
            sb.append("Headline: write exactly \"").append(accroche.trim())
              .append("\" once, keeping the French spelling and accents exactly, in large bold modern sans-serif typography, ")
              .append("perfectly legible, with strong contrast, integrated into the layout (not a plain caption).\n\n");
        }
        if (logo) {
            sb.append("Top-left corner (about 25% of the width and 12% of the height): keep it plain and uncluttered, ")
              .append("with no text or important detail, because the brand logo will be placed there afterwards.\n\n");
        }
        if (couleurs != null && !couleurs.isEmpty()) {
            sb.append("Brand colours: ").append(String.join(", ", couleurs)).append(".\n\n");
        }
        if (pourcentBandeau > 0) {
            sb.append("Bottom ").append(pourcentBandeau).append("% of the image: a plain solid ").append(couleurBandeau)
              .append(" band, completely empty (contact details will be added later).\n\n");
        }
        sb.append(accroche != null && !accroche.isBlank()
                ? "No other text, letters, numbers, URLs or watermarks anywhere."
                : "No text, letters, numbers, logos, URLs or watermarks anywhere.");
        return sb.toString();
    }

    /** Hauteur du bandeau en % de la hauteur de l'image, comme le dessine CompositionVisuel (relatif à la largeur). */
    static int partBandeau(String ratio, int nbContacts) {
        if (nbContacts == 0) return 0;
        String[] r = (ratio == null ? "1:1" : ratio).split(":");
        double l = Double.parseDouble(r[0]);
        double h = Double.parseDouble(r[1]);
        return (int) Math.ceil(CompositionVisuel.hauteurBandeau(nbContacts) * l / h) + 1;
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
