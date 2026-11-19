package com.okpost.reseaux;

import com.okpost.commun.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Page ou compte d'un client relié à OkPost. Un seul compte actif par réseau et par client. */
@Entity
@Table(name = "compte_social")
public class CompteSocial extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private String reseau;

    @Column(name = "compte_externe_id", nullable = false)
    private String compteExterneId;

    private String nom;

    @Column(name = "jeton_chiffre", nullable = false)
    private String jetonChiffre;

    private boolean actif;

    @Column(name = "expire_le")
    private Instant expireLe;

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    public String getReseau() { return reseau; }
    public void setReseau(String reseau) { this.reseau = reseau; }
    public String getCompteExterneId() { return compteExterneId; }
    public void setCompteExterneId(String compteExterneId) { this.compteExterneId = compteExterneId; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getJetonChiffre() { return jetonChiffre; }
    public void setJetonChiffre(String jetonChiffre) { this.jetonChiffre = jetonChiffre; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    @Column(length = 400)
    private String erreur;

    @Column(name = "verifie_le")
    private Instant verifieLe;

    public String getErreur() { return erreur; }
    public void setErreur(String erreur) { this.erreur = erreur; }
    public Instant getVerifieLe() { return verifieLe; }
    public void setVerifieLe(Instant verifieLe) { this.verifieLe = verifieLe; }
    public Instant getExpireLe() { return expireLe; }
    public void setExpireLe(Instant expireLe) { this.expireLe = expireLe; }
}
