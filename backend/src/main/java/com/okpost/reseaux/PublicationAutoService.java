package com.okpost.reseaux;

import com.okpost.calendrier.Calendrier;
import com.okpost.calendrier.CalendrierRepository;
import com.okpost.calendrier.Publication;
import com.okpost.calendrier.PublicationRepository;
import com.okpost.calendrier.StatutPublication;
import com.okpost.calendrier.VisuelGenere;
import com.okpost.calendrier.VisuelGenereRepository;
import com.okpost.client.Client;
import com.okpost.client.ClientRepository;
import com.okpost.client.StockageFichiers;
import com.okpost.commun.ApiException;
import com.okpost.config.OkpostProperties;
import com.okpost.securite.UtilisateurRepository;
import com.okpost.validation.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Programmation et publication automatique. Une publication VALIDÉE passe en PROGRAMMÉE avec une date et une heure ;
 * chaque minute, celles dont l'heure est passée sont publiées sur la Page reliée (Facebook pour l'instant).
 */
@Service
public class PublicationAutoService {

    private static final Logger log = LoggerFactory.getLogger(PublicationAutoService.class);
    static final int TENTATIVES_MAX = 3;

    private final PublicationRepository publications;
    private final CalendrierRepository calendriers;
    private final ClientRepository clients;
    private final CompteSocialRepository comptes;
    private final VisuelGenereRepository visuels;
    private final StockageFichiers stockage;
    private final MetaApi meta;
    private final Chiffreur chiffreur;
    private final TransactionTemplate tx;
    private final MailService mail;
    private final UtilisateurRepository utilisateurs;
    private final OkpostProperties props;

    public PublicationAutoService(PublicationRepository publications, CalendrierRepository calendriers,
                                  ClientRepository clients, CompteSocialRepository comptes, VisuelGenereRepository visuels,
                                  StockageFichiers stockage, MetaApi meta, Chiffreur chiffreur, TransactionTemplate tx,
                                  MailService mail, UtilisateurRepository utilisateurs, OkpostProperties props) {
        this.publications = publications;
        this.calendriers = calendriers;
        this.clients = clients;
        this.comptes = comptes;
        this.visuels = visuels;
        this.stockage = stockage;
        this.meta = meta;
        this.chiffreur = chiffreur;
        this.tx = tx;
        this.mail = mail;
        this.utilisateurs = utilisateurs;
        this.props = props;
    }

    /** Appelé après une validation du client : programme si le client a choisi la publication automatique. */
    public void apresValidation(Publication p) {
        Client c = clientDe(p);
        String raison = raisonNonProgrammable(c, p, true);
        if (raison != null) {
            log.info("Publication {} validée, non programmée automatiquement : {}", p.getId(), raison);
            return;
        }
        programmer(p, c, null);
        log.info("Publication {} programmée pour {}", p.getId(), p.getPublicationPrevue());
    }

    /** Pourquoi une publication ne peut pas être programmée (null si elle peut l'être). */
    public String raisonNonProgrammable(Client c, Publication p, boolean verifierAuto) {
        if (c == null) return "client introuvable";
        boolean instagram = ComptesSociauxService.INSTAGRAM.equals(p.getReseau());
        if (!instagram && !ComptesSociauxService.FACEBOOK.equals(p.getReseau())) {
            return "publication prévue sur " + p.getReseau() + " (seuls Facebook et Instagram sont pris en charge pour l'instant)";
        }
        if (verifierAuto && !c.isAutoPublication()) {
            return "la case « Publier automatiquement après validation » n'est pas cochée pour " + c.getNom();
        }
        if (instagram && !meta.instagramActif()) {
            return "la publication sur Instagram n'est pas encore activée sur le serveur (connexion Instagram non configurée)";
        }
        if (comptes.findFirstByClientIdAndReseauAndActifTrue(c.getId(), p.getReseau()).isEmpty()) {
            return instagram ? "aucun compte Instagram « utilisé » pour " + c.getNom() + " (relie la Page Facebook liée à son compte Instagram professionnel)"
                    : "aucune Page Facebook « utilisée » pour " + c.getNom();
        }
        if ("CARROUSEL".equals(p.getFormat())) {
            List<VisuelGenere> tous = visuels.findByPublicationIdInOrderByCreatedAtDesc(List.of(p.getId()));
            boolean montage = tous.stream().anyMatch(v -> v.getSource() == VisuelGenere.Source.MONTAGE);
            String mode = p.getModeCarrousel() != null ? p.getModeCarrousel() : montage ? "VIDEO" : "PHOTOS";
            if ("VIDEO".equals(mode) && !montage) {
                return "carrousel en vidéo défilante choisi, mais la vidéo n'est pas encore montée (monte-la, ou choisis « photos »)";
            }
        }
        if (instagram) {
            if ("TEXTE".equals(p.getFormat())) return "Instagram demande une image ou une vidéo (format TEXTE)";
            if (visuels.findByPublicationIdInOrderByCreatedAtDesc(List.of(p.getId())).isEmpty()) {
                return "aucune image ni vidéo n'est prête pour Instagram";
            }
        }
        return null;
    }

    public record BilanProgrammation(int programmees, List<String> ignorees) {}

    /** Programme d'un coup toutes les publications validées d'un calendrier. */
    public BilanProgrammation programmerValidees(List<Publication> pubs, Client c) {
        int n = 0;
        List<String> ignorees = new java.util.ArrayList<>();
        for (Publication p : pubs) {
            if (p.getStatut() != StatutPublication.VALIDEE) continue;
            String raison = raisonNonProgrammable(c, p, false);
            if (raison != null) {
                ignorees.add(p.getDatePrevue() + " : " + raison);
                continue;
            }
            programmer(p, c, null);
            n++;
        }
        return new BilanProgrammation(n, ignorees);
    }

    /** Programme une publication validée, à l'heure donnée ou à l'heure par défaut du client. */
    public void programmer(Publication p, Client c, Instant quand) {
        if (p.getStatut() != StatutPublication.VALIDEE && p.getStatut() != StatutPublication.PROGRAMMEE) {
            throw ApiException.requeteInvalide("Seule une publication validée peut être programmée");
        }
        String raison = raisonNonProgrammable(c, p, false);
        if (raison != null) {
            throw ApiException.requeteInvalide("Impossible de programmer : " + raison);
        }
        Instant prevue = quand != null ? quand : heureParDefaut(p, c);
        Instant auPlusTot = Instant.now().plus(Duration.ofMinutes(2));
        p.setPublicationPrevue(prevue.isBefore(auPlusTot) ? auPlusTot : prevue);
        p.setStatut(StatutPublication.PROGRAMMEE);
        p.setTentatives(0);
        p.setErreurPublication(null);
        publications.save(p);
    }

    public void annuler(Publication p) {
        if (p.getStatut() != StatutPublication.PROGRAMMEE) {
            throw ApiException.requeteInvalide("Cette publication n'est pas programmée");
        }
        p.setStatut(StatutPublication.VALIDEE);
        p.setPublicationPrevue(null);
        publications.save(p);
    }

    /** Chaque minute : publie ce qui est arrivé à échéance. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void publierEcheances() {
        List<UUID> ids = tx.execute(s -> publications
                .findTop20ByStatutAndPublicationPrevueBeforeAndTentativesLessThanOrderByPublicationPrevue(
                        StatutPublication.PROGRAMMEE, Instant.now(), TENTATIVES_MAX)
                .stream().map(Publication::getId).toList());
        if (ids != null) ids.forEach(this::publier);
    }

    /** Publie une publication tout de suite (bouton « Publier maintenant » ou échéance atteinte). */
    public void publier(UUID publicationId) {
        record Travail(String reseau, String format, String compteId, String jeton, String message, VisuelGenere media,
                       List<VisuelGenere> photos, Client client) {}
        Travail t;
        try {
            t = tx.execute(s -> {
                Publication p = publications.findById(publicationId).orElseThrow();
                Client c = clientDe(p);
                String reseau = ComptesSociauxService.INSTAGRAM.equals(p.getReseau()) ? ComptesSociauxService.INSTAGRAM
                        : ComptesSociauxService.FACEBOOK;
                CompteSocial compte = comptes.findFirstByClientIdAndReseauAndActifTrue(c.getId(), reseau)
                        .orElseThrow(() -> new MetaApi.MetaException("Aucun compte " + libelle(reseau) + " relié pour " + c.getNom()));
                List<VisuelGenere> tous = visuels.findByPublicationIdInOrderByCreatedAtDesc(List.of(p.getId()));
                List<VisuelGenere> photos = List.of();
                VisuelGenere media = tous.stream().findFirst().orElse(null);
                if ("CARROUSEL".equals(p.getFormat())) {
                    VisuelGenere montage = tous.stream().filter(v -> v.getSource() == VisuelGenere.Source.MONTAGE).findFirst().orElse(null);
                    List<VisuelGenere> diapos = com.okpost.calendrier.MontageService.imagesDuCarrousel(tous);
                    String mode = p.getModeCarrousel() != null ? p.getModeCarrousel() : montage != null ? "VIDEO" : "PHOTOS";
                    if ("VIDEO".equals(mode) && montage != null) {
                        media = montage;
                    } else if ("PHOTOS".equals(mode) && diapos.size() >= 2) {
                        photos = diapos;
                    } else if ("VIDEO".equals(mode)) {
                        throw new MetaApi.MetaException("Carrousel en vidéo défilante choisi, mais la vidéo n'est pas encore montée");
                    }
                }
                return new Travail(reseau, p.getFormat(), compte.getCompteExterneId(), chiffreur.dechiffrer(compte.getJetonChiffre()),
                        message(p), media, photos, c);
            });
        } catch (RuntimeException e) {
            echec(publicationId, e.getMessage());
            return;
        }
        try {
            log.info("Publication {} : envoi sur {} {}", publicationId, libelle(t.reseau()), t.compteId());
            MetaApi.Resultat r;
            if (ComptesSociauxService.INSTAGRAM.equals(t.reseau())) {
                r = publierSurInstagram(t.compteId(), t.jeton(), t.message(), t.format(), t.media(), t.photos());
            } else if (!t.photos().isEmpty()) {
                r = meta.publierPhotos(t.compteId(), t.jeton(),
                        t.photos().stream().map(v -> stockage.lire(v.getChemin())).toList(), t.message());
            } else if (t.media() == null) {
                r = meta.publierTexte(t.compteId(), t.jeton(), t.message());
            } else if (t.media().getTypeMedia() == VisuelGenere.TypeMedia.VIDEO) {
                r = meta.publierVideo(t.compteId(), t.jeton(), stockage.lire(t.media().getChemin()), t.message());
            } else {
                r = meta.publierPhoto(t.compteId(), t.jeton(), stockage.lire(t.media().getChemin()), t.message());
            }
            tx.executeWithoutResult(s -> publications.findById(publicationId).ifPresent(p -> {
                p.setStatut(StatutPublication.PUBLIEE);
                p.setPublieeLe(Instant.now());
                p.setPostExterneId(r.postId());
                p.setLienPost(r.lien());
                p.setErreurPublication(null);
                publications.save(p);
            }));
            log.info("Publication {} : publiée ({})", publicationId, r.lien());
        } catch (RuntimeException e) {
            log.warn("Publication {} : échec — {}", publicationId, e.getMessage());
            echec(publicationId, e.getMessage());
        }
    }

    static String libelle(String reseau) {
        return ComptesSociauxService.INSTAGRAM.equals(reseau) ? "Instagram" : "Facebook";
    }

    /** Instagram télécharge les médias depuis une adresse publique (celle de l'application, relayée par le frontend). */
    private MetaApi.Resultat publierSurInstagram(String igId, String jeton, String message, String format,
                                                 VisuelGenere media, List<VisuelGenere> photos) {
        String legende = legendeInstagram(message);
        boolean story = "STORY".equals(format);
        List<String> temporaires = new java.util.ArrayList<>();
        try {
            if (!photos.isEmpty()) {
                List<String> urls = photos.stream().limit(10).map(v -> urlPubliqueJpeg(v, temporaires)).toList();
                return meta.publierInstagramCarrousel(igId, jeton, urls, legende);
            }
            if (media == null) {
                throw new MetaApi.MetaException("Instagram demande une image ou une vidéo : ajoute un visuel à la publication");
            }
            if (media.getTypeMedia() == VisuelGenere.TypeMedia.VIDEO) {
                return meta.publierInstagramVideo(igId, jeton, urlPublique(media.getChemin()), legende, story);
            }
            return meta.publierInstagramImage(igId, jeton, urlPubliqueJpeg(media, temporaires), legende, story);
        } finally {
            temporaires.forEach(stockage::supprimer);
        }
    }

    private String urlPublique(String chemin) {
        return props.urlPublique().replaceAll("/+$", "") + com.okpost.client.ClientDtos.urlFichier(chemin);
    }

    /** Instagram n'accepte que le JPEG : une image PNG est convertie dans un fichier temporaire, supprimé après l'envoi. */
    private String urlPubliqueJpeg(VisuelGenere v, List<String> temporaires) {
        if ("image/jpeg".equals(v.getMime())) return urlPublique(v.getChemin());
        byte[] jpeg;
        try (var in = stockage.lire(v.getChemin()).getInputStream()) {
            jpeg = enJpeg(in.readAllBytes());
        } catch (IOException e) {
            throw new MetaApi.MetaException("Image illisible sur le serveur");
        }
        String chemin = stockage.enregistrer(new ByteArrayInputStream(jpeg), "jpg");
        temporaires.add(chemin);
        return urlPublique(chemin);
    }

    static byte[] enJpeg(byte[] octets) {
        try {
            BufferedImage src = javax.imageio.ImageIO.read(new ByteArrayInputStream(octets));
            if (src == null) {
                throw new MetaApi.MetaException("Instagram n'accepte que le JPEG : cette image (WebP ?) ne peut pas être convertie, téléverse-la en JPG ou PNG");
            }
            BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.drawImage(src, 0, 0, Color.WHITE, null);
            g.dispose();
            ByteArrayOutputStream sortie = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(out, "jpg", sortie);
            return sortie.toByteArray();
        } catch (IOException e) {
            throw new MetaApi.MetaException("Image illisible");
        }
    }

    /** Instagram limite la légende à 2 200 caractères et à 30 hashtags. */
    static String legendeInstagram(String message) {
        if (message == null) return "";
        StringBuilder sb = new StringBuilder();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("#[\\p{L}\\p{N}_]+").matcher(message);
        int dernier = 0;
        int n = 0;
        while (m.find()) {
            sb.append(message, dernier, m.start());
            if (++n <= 30) sb.append(m.group());
            dernier = m.end();
        }
        sb.append(message.substring(dernier));
        String texte = sb.toString().replaceAll("[ \\t]+\\n", "\n").trim();
        if (texte.length() <= 2200) return texte;
        String coupe = texte.substring(0, 2199);
        int i = coupe.lastIndexOf(' ');
        return (i > 1500 ? coupe.substring(0, i) : coupe) + "…";
    }

    /**
     * Une panne passagère est réessayée (après 5 min, puis 20 min, puis 1 h) ; un accès perdu ou un média refusé s'arrête
     * tout de suite, car réessayer ne servirait à rien : le CM est prévenu et l'alerte apparaît sur le tableau de bord.
     */
    private void echec(UUID publicationId, String message) {
        ErreursMeta.Nature nature = ErreursMeta.classer(message);
        tx.executeWithoutResult(s -> publications.findById(publicationId).ifPresent(p -> {
            int tentatives = nature == ErreursMeta.Nature.TEMPORAIRE ? p.getTentatives() + 1 : TENTATIVES_MAX;
            p.setTentatives(tentatives);
            p.setErreurPublication(message);
            p.setPublicationPrevue(Instant.now().plus(ErreursMeta.delai(tentatives)));
            publications.save(p);
            if (nature == ErreursMeta.Nature.ACCES) marquerCompteAReconnecter(p);
            if (tentatives >= TENTATIVES_MAX) prevenirCm(p, message, nature);
        }));
    }

    private void marquerCompteAReconnecter(Publication p) {
        Client c = clientDe(p);
        if (c == null) return;
        comptes.findFirstByClientIdAndReseauAndActifTrue(c.getId(), p.getReseau()).ifPresent(compte -> {
            compte.setErreur("L'accès de " + libelle(p.getReseau()) + " a été refusé lors d'une publication : reconnecte le compte.");
            comptes.save(compte);
        });
    }

    private void prevenirCm(Publication p, String message, ErreursMeta.Nature nature) {
        Client c = clientDe(p);
        if (c == null || c.getCmId() == null) return;
        String conseil = nature == ErreursMeta.Nature.ACCES
                ? "\n\nLe compte doit probablement être reconnecté : fiche du client, rubrique « Réseaux connectés », puis « Relier »."
                : nature == ErreursMeta.Nature.DEFINITIVE ? "\n\nLe média ou le texte est refusé : modifie la publication, puis reprogramme-la." : "";
        utilisateurs.findById(c.getCmId()).ifPresent(cm -> mail.envoyer(cm.getEmail(),
                "Publication non publiée pour " + c.getNom(),
                "La publication du " + p.getDatePrevue() + " n'a pas pu être publiée sur " + libelle(p.getReseau()) + ".\n\nRaison : " + message + conseil,
                props.urlPublique() + "/publications/" + p.getId(), "Voir la publication"));
    }

    static String message(Publication p) {
        return java.util.stream.Stream.of(
                        p.getLegende(),
                        p.getCta(),
                        p.getHashtags() == null || p.getHashtags().isEmpty() ? null
                                : p.getHashtags().stream().map(h -> h.startsWith("#") ? h : "#" + h).collect(Collectors.joining(" ")))
                .filter(x -> x != null && !x.isBlank())
                .collect(Collectors.joining("\n\n"));
    }

    static Instant heureParDefaut(Publication p, Client c) {
        LocalTime heure;
        try {
            heure = LocalTime.parse(c.getHeurePublication());
        } catch (RuntimeException e) {
            heure = LocalTime.of(18, 0);
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(c.getFuseau());
        } catch (RuntimeException e) {
            zone = ZoneId.of("Africa/Douala");
        }
        return p.getDatePrevue().atTime(heure).atZone(zone).toInstant();
    }

    private Client clientDe(Publication p) {
        Calendrier cal = calendriers.findById(p.getCalendrierId()).orElse(null);
        return cal == null ? null : clients.findById(cal.getClientId()).orElse(null);
    }
}
