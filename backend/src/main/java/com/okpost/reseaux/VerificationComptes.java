package com.okpost.reseaux;

import com.okpost.client.Client;
import com.okpost.client.ClientRepository;
import com.okpost.config.OkpostProperties;
import com.okpost.securite.UtilisateurRepository;
import com.okpost.validation.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Vérifie chaque jour que les jetons des comptes reliés fonctionnent encore. Un jeton révoqué (mot de passe changé, accès retiré)
 * est signalé tout de suite : alerte sur le tableau de bord et e-mail au CM, avant qu'une publication échoue.
 */
@Service
public class VerificationComptes {

    private static final Logger log = LoggerFactory.getLogger(VerificationComptes.class);

    private final CompteSocialRepository comptes;
    private final ClientRepository clients;
    private final UtilisateurRepository utilisateurs;
    private final MetaApi meta;
    private final Chiffreur chiffreur;
    private final MailService mail;
    private final OkpostProperties props;

    public VerificationComptes(CompteSocialRepository comptes, ClientRepository clients, UtilisateurRepository utilisateurs,
                               MetaApi meta, Chiffreur chiffreur, MailService mail, OkpostProperties props) {
        this.comptes = comptes;
        this.clients = clients;
        this.utilisateurs = utilisateurs;
        this.meta = meta;
        this.chiffreur = chiffreur;
        this.mail = mail;
        this.props = props;
    }

    @Scheduled(cron = "0 20 5 * * *", zone = "Africa/Douala")
    public void verifierTous() {
        List<UUID> ids = comptes.findAll().stream().map(CompteSocial::getId).toList();
        log.info("Vérification des jetons : {} compte(s)", ids.size());
        ids.forEach(this::verifier);
    }

    public void verifier(UUID compteId) {
        CompteSocial c = comptes.findById(compteId).orElse(null);
        if (c == null) return;
        String libelle = PublicationAutoService.libelle(c.getReseau());
        try {
            meta.verifierJeton(c.getCompteExterneId(), chiffreur.dechiffrer(c.getJetonChiffre()));
            c.setVerifieLe(Instant.now());
            c.setErreur(null);
            comptes.save(c);
        } catch (MetaApi.MetaException e) {
            // Une panne passagère de Meta ne doit pas déclencher une fausse alerte : seule une perte d'accès compte.
            if (ErreursMeta.classer(e.getMessage()) != ErreursMeta.Nature.ACCES) {
                log.info("Vérification du compte {} {} : réponse inexploitable ({}), à refaire demain", libelle, c.getNom(), e.getMessage());
                return;
            }
            boolean nouveau = c.getErreur() == null;
            c.setVerifieLe(Instant.now());
            c.setErreur("L'accès de " + libelle + " a été perdu ou refusé : reconnecte le compte. (" + SurveillanceService.resume(e.getMessage()) + ")");
            comptes.save(c);
            log.warn("Compte {} {} : accès perdu — {}", libelle, c.getNom(), e.getMessage());
            if (nouveau) prevenirCm(c, libelle);
        }
    }

    private void prevenirCm(CompteSocial c, String libelle) {
        Client client = clients.findById(c.getClientId()).orElse(null);
        if (client == null || client.getCmId() == null) return;
        utilisateurs.findById(client.getCmId()).ifPresent(cm -> mail.envoyer(cm.getEmail(),
                "Compte " + libelle + " à reconnecter pour " + client.getNom(),
                "OkPost n'a plus accès au compte " + libelle + " « " + c.getNom() + " » de " + client.getNom()
                        + " (mot de passe changé, accès retiré ou jeton expiré).\n\nLes publications prévues sur ce compte ne partiront pas "
                        + "tant qu'il n'est pas reconnecté : ouvre la fiche du client, rubrique « Réseaux connectés », puis clique sur « Relier ».",
                props.urlPublique() + "/clients/" + client.getId(), "Ouvrir la fiche du client"));
    }
}
