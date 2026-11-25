package com.okpost.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/** Supprime des fichiers stockés seulement une fois la transaction validée : si elle échoue, rien n'est perdu. */
public final class FichiersApresCommit {

    private static final Logger log = LoggerFactory.getLogger(FichiersApresCommit.class);

    private FichiersApresCommit() {}

    public static void supprimer(StockageFichiers stockage, List<String> chemins) {
        if (chemins == null || chemins.isEmpty()) return;
        List<String> copie = List.copyOf(chemins);
        Runnable effacer = () -> {
            for (String chemin : copie) {
                try {
                    stockage.supprimer(chemin);
                } catch (RuntimeException e) {
                    log.warn("Fichier non supprimé ({}) : {}", chemin, e.getMessage());
                }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    effacer.run();
                }
            });
        } else {
            effacer.run();
        }
    }
}
