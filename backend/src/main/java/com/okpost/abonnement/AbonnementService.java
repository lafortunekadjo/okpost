package com.okpost.abonnement;

import com.okpost.commun.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class AbonnementService {

    private final AbonnementRepository depot;

    public AbonnementService(AbonnementRepository depot) {
        this.depot = depot;
    }

    public Abonnement creerEssai(UUID workspaceId) {
        Abonnement a = new Abonnement();
        a.setWorkspaceId(workspaceId);
        a.setFormule(Formule.ESSAI);
        a.setDebut(LocalDate.now());
        a.setFin(LocalDate.now().plusDays(14));
        a.setStatut(Abonnement.Statut.ESSAI);
        a.setMode(Abonnement.Mode.ESSAI);
        return depot.save(a);
    }

    public Abonnement courant(UUID workspaceId) {
        return depot.findFirstByWorkspaceIdOrderByFinDesc(workspaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.PAYMENT_REQUIRED, "Aucun abonnement pour cet espace"));
    }

    public Formule formule(UUID workspaceId) {
        return courant(workspaceId).getFormule();
    }

    /** Bloque les écritures quand l'abonnement est en lecture seule. */
    public void exigerEcriture(UUID workspaceId) {
        if (courant(workspaceId).statutAu(LocalDate.now()) == Abonnement.Statut.LECTURE_SEULE) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED,
                    "Abonnement expiré : ton espace est en lecture seule. Renouvelle-le pour continuer.");
        }
    }
}
