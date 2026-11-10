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
        if (!ComptesSociauxService.FACEBOOK.equals(p.getReseau())) {
            return "publication prévue sur " + p.getReseau() + " (seul Facebook est pris en charge pour l'instant)";
        }
        if (verifierAuto && !c.isAutoPublication()) {
            return "la case « Publier automatiquement après validation » n'est pas cochée pour " + c.getNom();
        }
        if (comptes.findFirstByClientIdAndReseauAndActifTrue(c.getId(), ComptesSociauxService.FACEBOOK).isEmpty()) {
            return "aucune Page Facebook « utilisée » pour " + c.getNom();
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
        record Travail(String pageId, String jeton, String message, VisuelGenere media, List<VisuelGenere> photos,
                       Client client) {}
        Travail t;
        try {
            t = tx.execute(s -> {
                Publication p = publications.findById(publicationId).orElseThrow();
                Client c = clientDe(p);
                CompteSocial compte = comptes.findFirstByClientIdAndReseauAndActifTrue(c.getId(), ComptesSociauxService.FACEBOOK)
                        .orElseThrow(() -> new MetaApi.MetaException("Aucune Page Facebook reliée pour " + c.getNom()));
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
                return new Travail(compte.getCompteExterneId(), chiffreur.dechiffrer(compte.getJetonChiffre()),
                        message(p), media, photos, c);
            });
        } catch (RuntimeException e) {
            echec(publicationId, e.getMessage());
            return;
        }
        try {
            log.info("Publication {} : envoi sur la Page {}", publicationId, t.pageId());
            MetaApi.Resultat r;
            if (!t.photos().isEmpty()) {
                r = meta.publierPhotos(t.pageId(), t.jeton(),
                        t.photos().stream().map(v -> stockage.lire(v.getChemin())).toList(), t.message());
            } else if (t.media() == null) {
                r = meta.publierTexte(t.pageId(), t.jeton(), t.message());
            } else if (t.media().getTypeMedia() == VisuelGenere.TypeMedia.VIDEO) {
                r = meta.publierVideo(t.pageId(), t.jeton(), stockage.lire(t.media().getChemin()), t.message());
            } else {
                r = meta.publierPhoto(t.pageId(), t.jeton(), stockage.lire(t.media().getChemin()), t.message());
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

    private void echec(UUID publicationId, String message) {
        tx.executeWithoutResult(s -> publications.findById(publicationId).ifPresent(p -> {
            p.setTentatives(p.getTentatives() + 1);
            p.setErreurPublication(message);
            // Nouvel essai 10 minutes plus tard, jusqu'à 3 tentatives
            p.setPublicationPrevue(Instant.now().plus(Duration.ofMinutes(10)));
            publications.save(p);
            if (p.getTentatives() >= TENTATIVES_MAX) prevenirCm(p, message);
        }));
    }

    private void prevenirCm(Publication p, String message) {
        Client c = clientDe(p);
        if (c == null || c.getCmId() == null) return;
        utilisateurs.findById(c.getCmId()).ifPresent(cm -> mail.envoyer(cm.getEmail(),
                "Publication non publiée pour " + c.getNom(),
                "La publication du " + p.getDatePrevue() + " n'a pas pu être publiée sur Facebook après "
                        + TENTATIVES_MAX + " essais.\n\nRaison : " + message,
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
