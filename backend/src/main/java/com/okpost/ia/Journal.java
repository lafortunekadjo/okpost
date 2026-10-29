package com.okpost.ia;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class Journal {

    private final JournalIaRepository depot;

    public Journal(JournalIaRepository depot) {
        this.depot = depot;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void noter(UUID workspaceId, String fournisseur, String modele, String operation,
                      Integer entree, Integer sortie, long dureeMs, boolean succes, String erreur) {
        String err = erreur == null ? null : erreur.substring(0, Math.min(erreur.length(), 2000));
        depot.save(new JournalIa(workspaceId, fournisseur, modele, operation, entree, sortie, dureeMs, succes, err));
    }
}
