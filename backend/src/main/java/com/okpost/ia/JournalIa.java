package com.okpost.ia;

import com.okpost.commun.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "journal_ia")
public class JournalIa extends BaseEntity {

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(nullable = false)
    private String fournisseur;

    private String modele;

    @Column(nullable = false)
    private String operation;

    @Column(name = "jetons_entree")
    private Integer jetonsEntree;

    @Column(name = "jetons_sortie")
    private Integer jetonsSortie;

    @Column(name = "duree_ms", nullable = false)
    private long dureeMs;

    private boolean succes;

    private String erreur;

    protected JournalIa() {}

    public JournalIa(UUID workspaceId, String fournisseur, String modele, String operation,
                     Integer jetonsEntree, Integer jetonsSortie, long dureeMs, boolean succes, String erreur) {
        this.workspaceId = workspaceId;
        this.fournisseur = fournisseur;
        this.modele = modele;
        this.operation = operation;
        this.jetonsEntree = jetonsEntree;
        this.jetonsSortie = jetonsSortie;
        this.dureeMs = dureeMs;
        this.succes = succes;
        this.erreur = erreur;
    }

    public UUID getWorkspaceId() { return workspaceId; }
    public String getFournisseur() { return fournisseur; }
    public String getModele() { return modele; }
    public String getOperation() { return operation; }
    public Integer getJetonsEntree() { return jetonsEntree; }
    public Integer getJetonsSortie() { return jetonsSortie; }
    public long getDureeMs() { return dureeMs; }
    public boolean isSucces() { return succes; }
    public String getErreur() { return erreur; }
}
