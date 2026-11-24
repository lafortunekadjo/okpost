package com.okpost.admin;

import com.okpost.securite.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Journal d'activité. L'écriture ne doit jamais faire échouer l'action qui l'a déclenchée (inscription, publication…) :
 * elle a sa propre transaction et toute erreur est seulement consignée dans les logs.
 */
@Service
public class JournalService {

    private static final Logger log = LoggerFactory.getLogger(JournalService.class);
    /** Durée de conservation : les données personnelles (e-mails) ne sont pas gardées indéfiniment. */
    static final Duration CONSERVATION = Duration.ofDays(365);

    private final JournalActiviteRepository depot;
    private final TransactionTemplate transactionIndependante;

    public JournalService(JournalActiviteRepository depot, PlatformTransactionManager gestionnaire) {
        this.depot = depot;
        this.transactionIndependante = new TransactionTemplate(gestionnaire);
        this.transactionIndependante.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Action faite par l'utilisateur connecté. */
    public void enregistrer(Session s, String action, String cible, String detail) {
        enregistrer(s.workspaceId(), s.userId(), null, action, cible, detail);
    }

    /**
     * @param utilisateurId auteur s'il a un compte (son nom et son e-mail sont relus à l'affichage)
     * @param auteur        sinon, libellé de l'auteur (ex. « Marie (client) », « OkPost (automatique) »)
     */
    public void enregistrer(UUID workspaceId, UUID utilisateurId, String auteur, String action, String cible, String detail) {
        try {
            JournalActivite j = new JournalActivite();
            j.setWorkspaceId(workspaceId);
            j.setUtilisateurId(utilisateurId);
            j.setAuteur(coupe(auteur, 190));
            j.setAction(action);
            j.setCible(coupe(cible, 190));
            j.setDetail(coupe(detail, 500));
            transactionIndependante.executeWithoutResult(t -> depot.save(j));
        } catch (RuntimeException e) {
            log.warn("Journal d'activité : écriture impossible ({}) pour l'action {}", e.getMessage(), action);
        }
    }

    static String coupe(String texte, int max) {
        if (texte == null || texte.isBlank()) return null;
        String t = texte.trim().replaceAll("[\\p{Cntrl}]", " ");
        return t.length() > max ? t.substring(0, max) : t;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purger() {
        int n = depot.supprimerAvant(Instant.now().minus(CONSERVATION));
        if (n > 0) log.info("Journal d'activité : {} lignes de plus d'un an supprimées", n);
    }
}
