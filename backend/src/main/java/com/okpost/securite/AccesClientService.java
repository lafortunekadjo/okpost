package com.okpost.securite;

import com.okpost.admin.Actions;
import com.okpost.admin.JournalService;
import com.okpost.client.AccesClient;
import com.okpost.client.AccesClientRepository;
import com.okpost.client.Client;
import com.okpost.client.ClientRepository;
import com.okpost.client.ClientService;
import com.okpost.commun.ApiException;
import com.okpost.config.OkpostProperties;
import com.okpost.validation.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Collaboration sur un client : un utilisateur d'un autre espace (qui a déjà un compte OkPost) est invité comme administrateur d'UN client.
 * Il travaille alors dans l'espace du propriétaire, avec une session limitée à ce client : ni les autres clients, ni les membres,
 * ni l'abonnement, ni les réglages. Le propriétaire peut retirer l'accès à tout moment, et l'invité peut le quitter.
 */
@Service
public class AccesClientService {

    private static final Logger log = LoggerFactory.getLogger(AccesClientService.class);
    static final int COLLABORATEURS_MAX = 10;

    public record Collaborateur(UUID id, String nom, String email, Instant depuis) {}

    public record Partage(UUID clientId, String clientNom, String espace, Instant depuis) {}

    /** Jeton d'une session limitée à un client, avec ce qu'il faut pour répondre au navigateur. */
    public record Contexte(String jeton, Utilisateur utilisateur, UUID espaceId, UUID clientId) {}

    private final AccesClientRepository acces;
    private final ClientService clients;
    private final ClientRepository depotClients;
    private final UtilisateurRepository utilisateurs;
    private final WorkspaceRepository espaces;
    private final JwtService jwt;
    private final MailService mail;
    private final JournalService journal;
    private final OkpostProperties props;

    public AccesClientService(AccesClientRepository acces, ClientService clients, ClientRepository depotClients,
                              UtilisateurRepository utilisateurs, WorkspaceRepository espaces, JwtService jwt, MailService mail,
                              JournalService journal, OkpostProperties props) {
        this.acces = acces;
        this.clients = clients;
        this.depotClients = depotClients;
        this.utilisateurs = utilisateurs;
        this.espaces = espaces;
        this.jwt = jwt;
        this.mail = mail;
        this.journal = journal;
        this.props = props;
    }

    // ------------------------------------------------------------ côté propriétaire

    @Transactional(readOnly = true)
    public List<Collaborateur> lister(Session s, UUID clientId) {
        s.exigerAdmin();
        clients.charger(s, clientId);
        return acces.findByClientIdOrderByCreatedAt(clientId).stream().map(this::vue).toList();
    }

    @Transactional
    public Collaborateur inviter(Session s, UUID clientId, String email) {
        s.exigerAdmin();
        Client c = clients.charger(s, clientId);
        Utilisateur invite = utilisateurs.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .orElseThrow(() -> ApiException.requeteInvalide(
                        "Aucun compte OkPost avec cet e-mail : la personne doit d'abord créer son compte, puis tu l'invites."));
        if (!invite.isActif()) throw ApiException.requeteInvalide("Ce compte est désactivé");
        if (invite.getWorkspaceId().equals(s.workspaceId())) {
            throw ApiException.requeteInvalide("Cette personne fait déjà partie de ton espace : assigne-lui plutôt ce client comme responsable.");
        }
        if (acces.existsByClientIdAndUserId(clientId, invite.getId())) {
            throw ApiException.requeteInvalide("Cette personne a déjà accès à ce client");
        }
        if (acces.countByClientId(clientId) >= COLLABORATEURS_MAX) {
            throw ApiException.requeteInvalide(COLLABORATEURS_MAX + " collaborateurs au plus par client");
        }
        AccesClient a = new AccesClient();
        a.setWorkspaceId(s.workspaceId());
        a.setClientId(clientId);
        a.setUserId(invite.getId());
        a.setInvitePar(s.userId());
        acces.save(a);
        journal.enregistrer(s, Actions.ACCES_INVITE, c.getNom(), invite.getEmail());
        prevenir(invite, c);
        return vue(a);
    }

    @Transactional
    public void retirer(Session s, UUID clientId, UUID accesId) {
        s.exigerAdmin();
        Client c = clients.charger(s, clientId);
        AccesClient a = acces.findByIdAndClientId(accesId, clientId).orElseThrow(() -> ApiException.introuvable("Collaborateur"));
        String qui = utilisateurs.findById(a.getUserId()).map(Utilisateur::getEmail).orElse(null);
        acces.delete(a);
        journal.enregistrer(s, Actions.ACCES_RETIRE, c.getNom(), qui);
    }

    // ------------------------------------------------------------ côté invité

    /** Clients sur lesquels l'utilisateur a été invité (hors archivés). */
    @Transactional(readOnly = true)
    public List<Partage> partages(Session s) {
        return acces.findByUserIdOrderByCreatedAt(s.userId()).stream()
                .map(a -> depotClients.findById(a.getClientId())
                        .filter(c -> !c.isArchive())
                        .map(c -> new Partage(c.getId(), c.getNom(),
                                espaces.findById(a.getWorkspaceId()).map(Workspace::getNom).orElse(""), a.getCreatedAt()))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional
    public void quitter(Session s, UUID clientId) {
        acces.findByClientIdAndUserId(clientId, s.userId()).ifPresent(acces::delete);
    }

    /** Session limitée à ce client, dans l'espace du propriétaire. Refusée si l'accès n'existe plus. */
    @Transactional(readOnly = true)
    public Contexte contexte(Session s, UUID clientId) {
        AccesClient a = acces.findByClientIdAndUserId(clientId, s.userId())
                .orElseThrow(() -> ApiException.interdit("Tu n'as pas accès à ce client"));
        depotClients.findById(clientId)
                .filter(c -> !c.isArchive() && c.getWorkspaceId().equals(a.getWorkspaceId()))
                .orElseThrow(() -> ApiException.introuvable("Client"));
        Utilisateur u = utilisateurs.findById(s.userId()).filter(Utilisateur::isActif)
                .orElseThrow(() -> ApiException.interdit("Compte désactivé"));
        return new Contexte(jwt.jetonContexte(u, a.getWorkspaceId(), clientId), u, a.getWorkspaceId(), clientId);
    }

    // ------------------------------------------------------------

    private Collaborateur vue(AccesClient a) {
        Utilisateur u = utilisateurs.findById(a.getUserId()).orElse(null);
        return new Collaborateur(a.getId(), u == null ? "—" : u.getNom(), u == null ? "" : u.getEmail(), a.getCreatedAt());
    }

    private void prevenir(Utilisateur invite, Client c) {
        try {
            mail.envoyer(invite.getEmail(), "Tu as été invité(e) à collaborer sur « " + c.getNom() + " » dans OkPost",
                    "Bonjour " + invite.getNom() + ",\n\nTu peux désormais travailler sur le client « " + c.getNom()
                            + " » dans OkPost : calendriers, publications, visuels et réseaux de ce client.\n\n"
                            + "Connecte-toi, puis ouvre « Clients partagés avec moi » sur ton tableau de bord.",
                    props.urlPublique(), "Ouvrir OkPost");
        } catch (RuntimeException e) {
            log.warn("E-mail d'invitation non envoyé à {} : {}", invite.getEmail(), e.getMessage());
        }
    }
}
