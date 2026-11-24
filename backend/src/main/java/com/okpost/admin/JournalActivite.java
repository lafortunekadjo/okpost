package com.okpost.admin;

import com.okpost.commun.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Une ligne du journal d'activité : qui a fait quoi, sur quoi. */
@Entity
@Table(name = "journal_activite")
public class JournalActivite extends BaseEntity {

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "utilisateur_id")
    private UUID utilisateurId;

    private String auteur;

    @Column(nullable = false)
    private String action;

    private String cible;

    private String detail;

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public UUID getUtilisateurId() { return utilisateurId; }
    public void setUtilisateurId(UUID utilisateurId) { this.utilisateurId = utilisateurId; }
    public String getAuteur() { return auteur; }
    public void setAuteur(String auteur) { this.auteur = auteur; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getCible() { return cible; }
    public void setCible(String cible) { this.cible = cible; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
