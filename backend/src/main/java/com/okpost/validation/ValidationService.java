package com.okpost.validation;

import com.okpost.calendrier.Calendrier;
import com.okpost.calendrier.CalendrierRepository;
import com.okpost.calendrier.CalendrierService;
import com.okpost.calendrier.Publication;
import com.okpost.calendrier.PublicationRepository;
import com.okpost.calendrier.StatutPublication;
import com.okpost.client.Client;
import com.okpost.client.ClientRepository;
import com.okpost.client.ClientService;
import com.okpost.commun.ApiException;
import com.okpost.config.OkpostProperties;
import com.okpost.securite.JwtService;
import com.okpost.securite.Session;
import com.okpost.securite.UtilisateurRepository;
import com.okpost.validation.ValidationDtos.LienVue;
import com.okpost.validation.ValidationDtos.Portail;
import com.okpost.validation.ValidationDtos.PortailPublication;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ValidationService {

    private static final Set<StatutPublication> MODIFIABLES_PAR_CLIENT =
            Set.of(StatutPublication.A_VALIDER, StatutPublication.VALIDEE, StatutPublication.A_MODIFIER);

    private final LienValidationRepository liens;
    private final RetourClientRepository retours;
    private final PublicationRepository publications;
    private final CalendrierRepository calendriers;
    private final CalendrierService calendrierService;
    private final ClientRepository clients;
    private final ClientService clientService;
    private final UtilisateurRepository utilisateurs;
    private final MailService mail;
    private final OkpostProperties props;

    public ValidationService(LienValidationRepository liens, RetourClientRepository retours,
                             PublicationRepository publications, CalendrierRepository calendriers,
                             CalendrierService calendrierService, ClientRepository clients,
                             ClientService clientService, UtilisateurRepository utilisateurs, MailService mail,
                             OkpostProperties props) {
        this.liens = liens;
        this.retours = retours;
        this.publications = publications;
        this.calendriers = calendriers;
        this.calendrierService = calendrierService;
        this.clients = clients;
        this.clientService = clientService;
        this.utilisateurs = utilisateurs;
        this.mail = mail;
        this.props = props;
    }

    // ---------------------------------------------------------------- côté CM

    @Transactional
    public LienVue creerLien(Session s, UUID calendrierId, List<UUID> ids, String email) {
        Calendrier cal = calendrierService.charger(s, calendrierId);
        Set<UUID> demandes = new LinkedHashSet<>(ids);
        List<Publication> pubs = publications.findByIdInAndCalendrierId(demandes, cal.getId());
        if (pubs.size() != demandes.size()) {
            throw ApiException.requeteInvalide("Certaines publications n'appartiennent pas à ce calendrier");
        }
        for (Publication p : pubs) {
            if (!p.isDetailGenere() || p.getLegende() == null) {
                throw ApiException.requeteInvalide("La publication du " + p.getDatePrevue() + " n'est pas encore rédigée");
            }
            if (p.getStatut() == StatutPublication.IDEE || p.getStatut() == StatutPublication.BROUILLON
                    || p.getStatut() == StatutPublication.A_MODIFIER) {
                p.setStatut(StatutPublication.A_VALIDER);
            }
        }
        publications.saveAll(pubs);
        LienValidation l = new LienValidation();
        l.setWorkspaceId(s.workspaceId());
        l.setCalendrierId(cal.getId());
        l.setJeton(JwtService.jetonAleatoire());
        l.setExpireLe(Instant.now().plus(Duration.ofDays(props.validation().dureeJours())));
        l.setPublications(List.copyOf(demandes));
        l.setEmailDestinataire(email);
        liens.save(l);
        if (email != null && !email.isBlank()) {
            envoyerEmailLien(l, email);
        }
        return vue(l);
    }

    @Transactional
    public LienVue envoyerParEmail(Session s, UUID lienId, String email) {
        LienValidation l = liens.findByIdAndWorkspaceId(lienId, s.workspaceId())
                .orElseThrow(() -> ApiException.introuvable("Lien"));
        calendrierService.charger(s, l.getCalendrierId());
        if (!l.estValide()) {
            throw ApiException.requeteInvalide("Ce lien a expiré ou a été révoqué");
        }
        if (!mail.actif()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "L'envoi d'e-mails n'est pas encore configuré sur le serveur. Copie le lien pour l'envoyer par WhatsApp.");
        }
        l.setEmailDestinataire(email);
        liens.save(l);
        envoyerEmailLien(l, email);
        return vue(l);
    }

    @Transactional
    public void revoquer(Session s, UUID lienId) {
        LienValidation l = liens.findByIdAndWorkspaceId(lienId, s.workspaceId())
                .orElseThrow(() -> ApiException.introuvable("Lien"));
        calendrierService.charger(s, l.getCalendrierId());
        l.setRevoque(true);
        liens.save(l);
    }

    @Transactional(readOnly = true)
    public List<LienVue> lister(Session s, UUID calendrierId) {
        calendrierService.charger(s, calendrierId);
        return liens.findByCalendrierIdOrderByCreatedAtDesc(calendrierId).stream().map(this::vue).toList();
    }

    // ---------------------------------------------------------------- côté client (public)

    @Transactional(readOnly = true)
    public Portail portail(String jeton) {
        LienValidation l = lienValide(jeton);
        Calendrier cal = calendriers.findById(l.getCalendrierId()).orElseThrow(() -> ApiException.introuvable("Lien"));
        Client client = clients.findById(cal.getClientId()).orElseThrow(() -> ApiException.introuvable("Lien"));
        List<Publication> pubs = publications.findByIdInAndCalendrierId(l.getPublications(), cal.getId()).stream()
                .sorted((a, b) -> a.getDatePrevue().compareTo(b.getDatePrevue())).toList();
        Map<UUID, RetourClient> derniers = retours.findByPublicationIdInOrderByCreatedAtDesc(
                        pubs.stream().map(Publication::getId).toList()).stream()
                .collect(Collectors.toMap(RetourClient::getPublicationId, Function.identity(), (a, b) -> a));
        List<PortailPublication> vues = pubs.stream().map(p -> {
            RetourClient r = derniers.get(p.getId());
            return new PortailPublication(p.getId(), p.getDatePrevue(), p.getReseau(), p.getFormat(), p.getLegende(),
                    p.getHashtags(), p.getCta(), p.getBrief(), p.getScenario(), p.getStatut().name(),
                    r == null ? null : r.getDecision().name(), r == null ? null : r.getCommentaire());
        }).toList();
        int validees = (int) pubs.stream().filter(p -> p.getStatut() == StatutPublication.VALIDEE).count();
        int aModifier = (int) pubs.stream().filter(p -> p.getStatut() == StatutPublication.A_MODIFIER).count();
        String couleur = client.getCouleurs() == null || client.getCouleurs().isEmpty() ? null : client.getCouleurs().get(0);
        return new Portail(client.getNom(), clientService.logoUrl(client.getId()), couleur, cal.getPeriodeDebut(),
                cal.getPeriodeFin(), l.getExpireLe(), pubs.size(), validees, aModifier, vues);
    }

    @Transactional
    public void decider(String jeton, UUID publicationId, RetourClient.Decision decision, String commentaire, String ipHash) {
        if (decision == RetourClient.Decision.MODIFIEE) {
            throw ApiException.requeteInvalide("Utilise la modification de légende pour proposer un nouveau texte");
        }
        if (decision == RetourClient.Decision.A_MODIFIER && (commentaire == null || commentaire.isBlank())) {
            throw ApiException.requeteInvalide("Explique ce qu'il faut modifier");
        }
        LienValidation l = lienValide(jeton);
        Publication p = publicationDuLien(l, publicationId);
        p.setStatut(decision == RetourClient.Decision.OK ? StatutPublication.VALIDEE : StatutPublication.A_MODIFIER);
        publications.save(p);
        enregistrerRetour(l, p, decision, commentaire, null, null, ipHash);
        notifier(l, p, decision, commentaire);
    }

    @Transactional
    public void modifierLegende(String jeton, UUID publicationId, String legende, String commentaire, String ipHash) {
        LienValidation l = lienValide(jeton);
        Publication p = publicationDuLien(l, publicationId);
        String origine = p.getLegende();
        p.setLegende(legende.trim());
        p.setStatut(StatutPublication.VALIDEE);
        publications.save(p);
        enregistrerRetour(l, p, RetourClient.Decision.MODIFIEE, commentaire, legende.trim(), origine, ipHash);
        notifier(l, p, RetourClient.Decision.MODIFIEE, commentaire);
    }

    // ----------------------------------------------------------------

    private LienValidation lienValide(String jeton) {
        LienValidation l = liens.findByJeton(jeton).orElseThrow(() -> ApiException.introuvable("Lien"));
        if (!l.estValide()) {
            throw new ApiException(HttpStatus.GONE, "Ce lien de validation a expiré. Demande un nouveau lien à ton community manager.");
        }
        return l;
    }

    private Publication publicationDuLien(LienValidation l, UUID publicationId) {
        if (!l.getPublications().contains(publicationId)) {
            throw ApiException.introuvable("Publication");
        }
        Publication p = publications.findById(publicationId).orElseThrow(() -> ApiException.introuvable("Publication"));
        if (!MODIFIABLES_PAR_CLIENT.contains(p.getStatut())) {
            throw ApiException.requeteInvalide("Cette publication est déjà programmée ou publiée");
        }
        return p;
    }

    private void enregistrerRetour(LienValidation l, Publication p, RetourClient.Decision d, String commentaire,
                                   String proposee, String origine, String ipHash) {
        RetourClient r = new RetourClient();
        r.setPublicationId(p.getId());
        r.setLienId(l.getId());
        r.setDecision(d);
        r.setCommentaire(commentaire);
        r.setLegendeProposee(proposee);
        r.setLegendeOrigine(origine);
        r.setIpHash(ipHash);
        retours.save(r);
    }

    private void notifier(LienValidation l, Publication p, RetourClient.Decision d, String commentaire) {
        Calendrier cal = calendriers.findById(l.getCalendrierId()).orElse(null);
        if (cal == null) return;
        Client client = clients.findById(cal.getClientId()).orElse(null);
        if (client == null || client.getCmId() == null) return;
        utilisateurs.findById(client.getCmId()).ifPresent(cm -> {
            String url = props.urlPublique() + "/calendriers/" + cal.getId();
            Set<StatutPublication> restants = new HashSet<>();
            publications.findByIdInAndCalendrierId(l.getPublications(), cal.getId())
                    .forEach(x -> restants.add(x.getId().equals(p.getId()) ? p.getStatut() : x.getStatut()));
            if (d != RetourClient.Decision.OK) {
                String quoi = d == RetourClient.Decision.A_MODIFIER ? "demande une modification" : "a modifié la légende";
                mail.envoyer(cm.getEmail(), client.getNom() + " " + quoi,
                        client.getNom() + " " + quoi + " sur la publication du " + p.getDatePrevue() + " (" + p.getReseau() + ")."
                                + (commentaire == null || commentaire.isBlank() ? "" : "\n\nCommentaire : " + commentaire),
                        url, "Voir le calendrier");
            } else if (!restants.contains(StatutPublication.A_VALIDER)) {
                mail.envoyer(cm.getEmail(), client.getNom() + " a terminé la validation",
                        client.getNom() + " a donné son avis sur toutes les publications envoyées.", url, "Voir le calendrier");
            }
        });
    }

    private void envoyerEmailLien(LienValidation l, String email) {
        Calendrier cal = calendriers.findById(l.getCalendrierId()).orElseThrow();
        Client client = clients.findById(cal.getClientId()).orElseThrow();
        mail.envoyer(email, "Publications à valider pour " + client.getNom(),
                "Bonjour,\n\n" + l.getPublications().size() + " publication(s) prévues entre le " + cal.getPeriodeDebut()
                        + " et le " + cal.getPeriodeFin() + " attendent votre validation.\n"
                        + "Vous pouvez valider, modifier la légende ou demander une modification, depuis votre téléphone.",
                url(l), "Voir et valider les publications");
    }

    private String url(LienValidation l) {
        return props.urlPublique() + "/v/" + l.getJeton();
    }

    private LienVue vue(LienValidation l) {
        return new LienVue(l.getId(), url(l), l.getExpireLe(), l.isRevoque(), l.getPublications().size(),
                l.getEmailDestinataire(), l.getCreatedAt());
    }
}
