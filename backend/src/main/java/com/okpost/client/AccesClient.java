package com.okpost.client;

import com.okpost.commun.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Accès d'un utilisateur d'un autre espace à UN client : il en est administrateur, sans rien voir d'autre de l'espace. */
@Entity
@Table(name = "acces_client")
public class AccesClient extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "invite_par")
    private UUID invitePar;

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getInvitePar() { return invitePar; }
    public void setInvitePar(UUID invitePar) { this.invitePar = invitePar; }
}
