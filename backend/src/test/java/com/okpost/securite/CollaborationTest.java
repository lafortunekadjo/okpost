package com.okpost.securite;

import com.okpost.abonnement.AbonnementService;
import com.okpost.admin.JournalService;
import com.okpost.client.AccesClient;
import com.okpost.client.AccesClientRepository;
import com.okpost.client.Client;
import com.okpost.client.ClientAssetRepository;
import com.okpost.client.ClientRepository;
import com.okpost.client.ClientService;
import com.okpost.client.StockageFichiers;
import com.okpost.commun.ApiException;
import com.okpost.config.OkpostProperties;
import com.okpost.validation.MailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollaborationTest {

    private final UUID espaceProprietaire = UUID.randomUUID();
    private final UUID espaceInvite = UUID.randomUUID();
    private final UUID invite = UUID.randomUUID();
    private final UUID proprietaire = UUID.randomUUID();

    private final ClientRepository depotClients = mock(ClientRepository.class);
    private final AccesClientRepository acces = mock(AccesClientRepository.class);
    private final ClientService clients = new ClientService(depotClients, mock(ClientAssetRepository.class), mock(StockageFichiers.class),
            mock(AbonnementService.class), mock(JournalService.class), acces);

    private Client client(UUID id, boolean archive) {
        Client c = new Client();
        c.setId(id);
        c.setNom("Boutique " + id.toString().substring(0, 4));
        c.setWorkspaceId(espaceProprietaire);
        c.setArchive(archive);
        return c;
    }

    private Session collab(UUID client) {
        return new Session(invite, espaceProprietaire, Role.COLLAB, client);
    }

    @AfterEach
    void nettoyer() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------ un invité ne voit qu'un client

    @Test
    void lInviteNeChargeQueLeClientPartageEtSeulementTantQueLAccesExiste() {
        Client partage = client(UUID.randomUUID(), false);
        Client autre = client(UUID.randomUUID(), false);
        when(depotClients.findByIdAndWorkspaceId(partage.getId(), espaceProprietaire)).thenReturn(Optional.of(partage));
        when(depotClients.findByIdAndWorkspaceId(autre.getId(), espaceProprietaire)).thenReturn(Optional.of(autre));
        Session s = collab(partage.getId());

        when(acces.existsByClientIdAndUserId(partage.getId(), invite)).thenReturn(true);
        assertThat(clients.charger(s, partage.getId())).isSameAs(partage);
        // Un autre client du même espace reste introuvable, même avec son identifiant exact
        assertThatThrownBy(() -> clients.charger(s, autre.getId())).isInstanceOf(ApiException.class).hasMessageContaining("introuvable");

        // Accès retiré par le propriétaire : le jeton encore valide ne donne plus rien
        when(acces.existsByClientIdAndUserId(partage.getId(), invite)).thenReturn(false);
        assertThatThrownBy(() -> clients.charger(s, partage.getId())).isInstanceOf(ApiException.class);
    }

    @Test
    void unClientArchiveEstInvisiblePourLInvite() {
        Client archive = client(UUID.randomUUID(), true);
        when(depotClients.findByIdAndWorkspaceId(archive.getId(), espaceProprietaire)).thenReturn(Optional.of(archive));
        when(acces.existsByClientIdAndUserId(archive.getId(), invite)).thenReturn(true);
        assertThatThrownBy(() -> clients.charger(collab(archive.getId()), archive.getId())).isInstanceOf(ApiException.class);
        assertThat(clients.lister(collab(archive.getId()))).isEmpty();
    }

    @Test
    void laListeDeLInviteNeContientQueSonClient() {
        Client partage = client(UUID.randomUUID(), false);
        when(depotClients.findByIdAndWorkspaceId(partage.getId(), espaceProprietaire)).thenReturn(Optional.of(partage));
        when(acces.existsByClientIdAndUserId(partage.getId(), invite)).thenReturn(true);
        assertThat(clients.lister(collab(partage.getId()))).containsExactly(partage);
        // Jamais la liste de l'espace : le dépôt des clients de l'espace n'est pas interrogé
        verify(depotClients, never()).findByWorkspaceIdAndArchiveFalseOrderByNom(any());
        // Une session sans client autorisé ne voit rien
        assertThat(clients.lister(new Session(invite, espaceProprietaire, Role.COLLAB, null))).isEmpty();
    }

    @Test
    void lInviteNePeutPasCreerDeClient() {
        assertThatThrownBy(() -> clients.creer(collab(UUID.randomUUID()), null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatut()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void lesEcransDeTouteLEspaceSontInterditsALInvite() {
        Session s = collab(UUID.randomUUID());
        assertThat(s.estCollaborateur()).isTrue();
        assertThat(s.estAdmin()).isFalse();
        assertThatThrownBy(s::exigerProprietaire).isInstanceOf(ApiException.class).hasMessageContaining("invité");
        assertThatThrownBy(s::exigerAdmin).isInstanceOf(ApiException.class);
        assertThatThrownBy(s::exigerSuperAdmin).isInstanceOf(ApiException.class);
        new Session(proprietaire, espaceProprietaire, Role.ADMIN).exigerProprietaire();
        new Session(proprietaire, espaceProprietaire, Role.CM).exigerProprietaire();
    }

    @Test
    void laSessionLitLeRoleEtLeClientAutoriseDansLeJeton() {
        UUID clientId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject(invite.toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("ws", espaceProprietaire.toString()).claim("role", "COLLAB").claim("client", clientId.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        Session s = Session.courante();
        assertThat(s.role()).isEqualTo(Role.COLLAB);
        assertThat(s.clientRestreint()).isEqualTo(clientId);
        assertThat(s.workspaceId()).isEqualTo(espaceProprietaire);

        Jwt normal = Jwt.withTokenValue("t").header("alg", "HS256").subject(proprietaire.toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("ws", espaceProprietaire.toString()).claim("role", "ADMIN").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(normal));
        assertThat(Session.courante().clientRestreint()).isNull();
        assertThat(Session.courante().estCollaborateur()).isFalse();
    }

    // ------------------------------------------------------------ invitation

    private final UtilisateurRepository utilisateurs = mock(UtilisateurRepository.class);
    private final ClientService clientsMock = mock(ClientService.class);
    private final JournalService journal = mock(JournalService.class);
    private final MailService mail = mock(MailService.class);
    private final JwtService jwt = mock(JwtService.class);
    private final AccesClientService service = new AccesClientService(acces, clientsMock, depotClients, utilisateurs,
            mock(WorkspaceRepository.class), jwt, mail, journal, mock(OkpostProperties.class));
    private final Session proprio = new Session(proprietaire, espaceProprietaire, Role.ADMIN);
    private Client aPartager;

    @BeforeEach
    void preparer() {
        aPartager = client(UUID.randomUUID(), false);
        when(clientsMock.charger(any(), any())).thenReturn(aPartager);
        when(acces.save(any(AccesClient.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Utilisateur utilisateur(UUID id, UUID espace, String email) {
        Utilisateur u = new Utilisateur();
        u.setId(id);
        u.setWorkspaceId(espace);
        u.setEmail(email);
        u.setNom("Awa");
        u.setRole(Role.ADMIN);
        return u;
    }

    @Test
    void uneInvitationCreeLAccesPrevientLaPersonneEtEstJournalisee() {
        when(utilisateurs.findByEmailIgnoreCase("Awa@Exemple.cm")).thenReturn(Optional.of(utilisateur(invite, espaceInvite, "awa@exemple.cm")));
        when(utilisateurs.findById(invite)).thenReturn(Optional.of(utilisateur(invite, espaceInvite, "awa@exemple.cm")));
        var c = service.inviter(proprio, aPartager.getId(), " Awa@Exemple.cm ");
        assertThat(c.email()).isEqualTo("awa@exemple.cm");
        verify(acces).save(any(AccesClient.class));
        verify(journal).enregistrer(any(Session.class), anyString(), anyString(), anyString());
        verify(mail).envoyer(anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void lInvitationRefuseLesCasAbusifs() {
        when(utilisateurs.findByEmailIgnoreCase("inconnu@exemple.cm")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.inviter(proprio, aPartager.getId(), "inconnu@exemple.cm"))
                .isInstanceOf(ApiException.class).hasMessageContaining("Aucun compte OkPost");

        UUID collegue = UUID.randomUUID();
        when(utilisateurs.findByEmailIgnoreCase("collegue@exemple.cm")).thenReturn(Optional.of(utilisateur(collegue, espaceProprietaire, "collegue@exemple.cm")));
        assertThatThrownBy(() -> service.inviter(proprio, aPartager.getId(), "collegue@exemple.cm"))
                .isInstanceOf(ApiException.class).hasMessageContaining("déjà partie de ton espace");

        when(utilisateurs.findByEmailIgnoreCase("awa@exemple.cm")).thenReturn(Optional.of(utilisateur(invite, espaceInvite, "awa@exemple.cm")));
        when(acces.existsByClientIdAndUserId(aPartager.getId(), invite)).thenReturn(true);
        assertThatThrownBy(() -> service.inviter(proprio, aPartager.getId(), "awa@exemple.cm"))
                .isInstanceOf(ApiException.class).hasMessageContaining("déjà accès");

        when(acces.existsByClientIdAndUserId(aPartager.getId(), invite)).thenReturn(false);
        when(acces.countByClientId(aPartager.getId())).thenReturn((long) AccesClientService.COLLABORATEURS_MAX);
        assertThatThrownBy(() -> service.inviter(proprio, aPartager.getId(), "awa@exemple.cm"))
                .isInstanceOf(ApiException.class).hasMessageContaining("collaborateurs au plus");
        verify(acces, never()).save(any(AccesClient.class));
    }

    @Test
    void seulUnAdministrateurInviteEtUnInviteNeLePeutPas() {
        Session cm = new Session(proprietaire, espaceProprietaire, Role.CM);
        assertThatThrownBy(() -> service.inviter(cm, aPartager.getId(), "awa@exemple.cm")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.inviter(collab(aPartager.getId()), aPartager.getId(), "awa@exemple.cm")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.lister(collab(aPartager.getId()), aPartager.getId())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.retirer(collab(aPartager.getId()), aPartager.getId(), UUID.randomUUID())).isInstanceOf(ApiException.class);
        verify(acces, never()).save(any(AccesClient.class));
    }

    // ------------------------------------------------------------ session limitée

    @Test
    void laSessionLimiteeNEstDelivreeQueSiLAccesExiste() {
        Session moi = new Session(invite, espaceInvite, Role.ADMIN);
        when(acces.findByClientIdAndUserId(aPartager.getId(), invite)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.contexte(moi, aPartager.getId()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatut()).isEqualTo(HttpStatus.FORBIDDEN));

        AccesClient a = new AccesClient();
        a.setWorkspaceId(espaceProprietaire);
        a.setClientId(aPartager.getId());
        a.setUserId(invite);
        when(acces.findByClientIdAndUserId(aPartager.getId(), invite)).thenReturn(Optional.of(a));
        when(depotClients.findById(aPartager.getId())).thenReturn(Optional.of(aPartager));
        Utilisateur u = utilisateur(invite, espaceInvite, "awa@exemple.cm");
        when(utilisateurs.findById(invite)).thenReturn(Optional.of(u));
        when(jwt.jetonContexte(u, espaceProprietaire, aPartager.getId())).thenReturn("jeton-contexte");
        var ctx = service.contexte(moi, aPartager.getId());
        assertThat(ctx.jeton()).isEqualTo("jeton-contexte");
        assertThat(ctx.espaceId()).isEqualTo(espaceProprietaire);

        // Client archivé ou d'un autre espace : pas de session
        aPartager.setArchive(true);
        assertThatThrownBy(() -> service.contexte(moi, aPartager.getId())).isInstanceOf(ApiException.class);
        aPartager.setArchive(false);
        aPartager.setWorkspaceId(UUID.randomUUID());
        assertThatThrownBy(() -> service.contexte(moi, aPartager.getId())).isInstanceOf(ApiException.class);
    }

    @Test
    void lInvitePeutQuitterEtSesClientsPartagesSontListes() {
        AccesClient a = new AccesClient();
        a.setWorkspaceId(espaceProprietaire);
        a.setClientId(aPartager.getId());
        a.setUserId(invite);
        when(acces.findByClientIdAndUserId(aPartager.getId(), invite)).thenReturn(Optional.of(a));
        service.quitter(new Session(invite, espaceInvite, Role.ADMIN), aPartager.getId());
        verify(acces).delete(a);

        when(acces.findByUserIdOrderByCreatedAt(invite)).thenReturn(List.of(a));
        when(depotClients.findById(aPartager.getId())).thenReturn(Optional.of(aPartager));
        assertThat(service.partages(new Session(invite, espaceInvite, Role.ADMIN))).hasSize(1);
        aPartager.setArchive(true);
        assertThat(service.partages(new Session(invite, espaceInvite, Role.ADMIN))).isEmpty();
    }
}
