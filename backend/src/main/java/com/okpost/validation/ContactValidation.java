package com.okpost.validation;

import com.okpost.commun.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Personne autorisée à valider les publications d'un client, identifiée par son code personnel. */
@Entity
@Table(name = "contact_validation")
public class ContactValidation extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private String nom;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    private boolean actif = true;

    @Column(name = "derniere_utilisation")
    private Instant derniereUtilisation;

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public Instant getDerniereUtilisation() { return derniereUtilisation; }
    public void setDerniereUtilisation(Instant derniereUtilisation) { this.derniereUtilisation = derniereUtilisation; }
}
