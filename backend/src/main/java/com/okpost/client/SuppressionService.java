package com.okpost.client;

import com.okpost.abonnement.AbonnementService;
import com.okpost.admin.Actions;
import com.okpost.admin.JournalService;
import com.okpost.commun.ApiException;
import com.okpost.securite.Role;
import com.okpost.securite.Session;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Archivage, restauration et suppression définitive d'un client.
 * Archiver masque le client et annule ses programmations (sinon des publications partiraient pour un client qu'on ne voit plus) ;
 * la suppression définitive n'est possible que sur un client archivé, par un administrateur, et efface tout, fichiers compris.
 */
@Service
public class SuppressionService {

    private final ClientService clients;
    private final ClientRepository depot;
    private final JdbcTemplate jdbc;
    private final StockageFichiers stockage;
    private final JournalService journal;
    private final AbonnementService abonnements;

    public SuppressionService(ClientService clients, ClientRepository depot, JdbcTemplate jdbc, StockageFichiers stockage,
                              JournalService journal, AbonnementService abonnements) {
        this.clients = clients;
        this.depot = depot;
        this.jdbc = jdbc;
        this.stockage = stockage;
        this.journal = journal;
        this.abonnements = abonnements;
    }

    @Transactional(readOnly = true)
    public List<Client> listerArchives(Session s) {
        s.exigerProprietaire();
        return s.role() == Role.CM
                ? depot.findByWorkspaceIdAndCmIdAndArchiveTrueOrderByNom(s.workspaceId(), s.userId())
                : depot.findByWorkspaceIdAndArchiveTrueOrderByNom(s.workspaceId());
    }

    /** @return nombre de programmations annulées */
    @Transactional
    public int archiver(Session s, UUID id) {
        s.exigerProprietaire();
        Client c = clients.charger(s, id);
        int annulees = jdbc.update("""
                UPDATE publication SET statut = 'VALIDEE', publication_prevue = NULL, updated_at = now()
                WHERE statut = 'PROGRAMMEE' AND calendrier_id IN (SELECT id FROM calendrier WHERE client_id = ?)
                """, id);
        c.setArchive(true);
        depot.save(c);
        journal.enregistrer(s, Actions.CLIENT_ARCHIVE, c.getNom(), annulees > 0 ? annulees + " programmation(s) annulée(s)" : null);
        return annulees;
    }

    @Transactional
    public void restaurer(Session s, UUID id) {
        s.exigerProprietaire();
        abonnements.exigerEcriture(s.workspaceId());
        Client c = clients.charger(s, id);
        if (!c.isArchive()) return;
        int max = abonnements.formule(s.workspaceId()).maxClients();
        if (depot.countByWorkspaceIdAndArchiveFalse(s.workspaceId()) >= max) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED,
                    "Ta formule permet " + max + " client(s) actifs. Archive un autre client ou passe à la formule supérieure.");
        }
        c.setArchive(false);
        depot.save(c);
        journal.enregistrer(s, Actions.CLIENT_RESTAURE, c.getNom(), null);
    }

    /** Efface le client, ses calendriers, publications, visuels, photos, comptes reliés et codes de validation. Irréversible. */
    @Transactional
    public void supprimerDefinitivement(Session s, UUID id) {
        s.exigerProprietaire();
        s.exigerAdmin();
        Client c = clients.charger(s, id);
        if (!c.isArchive()) {
            throw ApiException.requeteInvalide("Archive d'abord ce client : la suppression définitive ne s'applique qu'à un client archivé.");
        }
        List<String> fichiers = jdbc.queryForList("""
                SELECT v.chemin FROM visuel_genere v
                  JOIN publication p ON p.id = v.publication_id
                  JOIN calendrier k ON k.id = p.calendrier_id
                 WHERE k.client_id = ?
                UNION
                SELECT a.chemin FROM client_asset a WHERE a.client_id = ?
                """, String.class, id, id);
        // Les calendriers référencent le client sans suppression en cascade : ils partent d'abord (leurs publications, visuels et retours suivent)
        jdbc.update("DELETE FROM calendrier WHERE client_id = ?", id);
        depot.delete(c);
        depot.flush();
        FichiersApresCommit.supprimer(stockage, fichiers);
        journal.enregistrer(s, Actions.CLIENT_SUPPRIME, c.getNom(), fichiers.size() + " fichier(s) effacé(s)");
    }
}
