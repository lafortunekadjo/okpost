package com.okpost.ia;

import com.okpost.config.OkpostProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Régule les appels aux fournisseurs : un appel à la fois, un débit maximum par minute,
 * et des relances espacées quand le fournisseur répond « trop de requêtes » (429).
 * File en mémoire pour le MVP ; Redis si plusieurs instances un jour.
 */
@Component
public class FileAttenteIA {

    private static final Logger log = LoggerFactory.getLogger(FileAttenteIA.class);
    private static final long[] ATTENTES_MS = {20_000, 45_000, 90_000};

    private final long intervalleMs;
    private long dernierAppel;

    public FileAttenteIA(OkpostProperties props) {
        int debit = Math.max(1, props.ia().debitParMinute());
        this.intervalleMs = 60_000L / debit;
    }

    public synchronized <T> T executer(Supplier<T> appel) {
        for (int essai = 0; ; essai++) {
            patienter();
            try {
                return appel.get();
            } catch (RuntimeException e) {
                if (!estLimiteDeDebit(e) || essai >= ATTENTES_MS.length) {
                    throw e;
                }
                log.warn("Limite de débit du fournisseur IA atteinte, nouvel essai dans {} s", ATTENTES_MS[essai] / 1000);
                dormir(ATTENTES_MS[essai]);
            }
        }
    }

    private void patienter() {
        long attente = dernierAppel + intervalleMs - System.currentTimeMillis();
        if (attente > 0) {
            dormir(attente);
        }
        dernierAppel = System.currentTimeMillis();
    }

    static boolean estLimiteDeDebit(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String m = String.valueOf(t.getMessage());
            if (m.contains("429") || m.contains("RESOURCE_EXHAUSTED") || m.contains("rate_limit") || m.contains("overloaded")) {
                return true;
            }
        }
        return false;
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Génération interrompue", ie);
        }
    }
}
