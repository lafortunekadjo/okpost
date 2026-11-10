package com.okpost.validation;

import com.okpost.commun.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "lien_validation")
public class LienValidation extends BaseEntity {

    /** VALIDATION : le client valide ou commente. CONSULTATION : lecture seule (partage du calendrier). */
    public enum Type { VALIDATION, CONSULTATION }

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false)
    private Type type = Type.VALIDATION;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "calendrier_id", nullable = false)
    private UUID calendrierId;

    /** Jeton aléatoire de 32 octets : c'est la seule clé d'accès au portail. */
    @Column(nullable = false, unique = true)
    private String jeton;

    @Column(name = "expire_le", nullable = false)
    private Instant expireLe;

    private boolean revoque;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<UUID> publications = new ArrayList<>();

    @Column(name = "email_destinataire")
    private String emailDestinataire;

    public boolean estValide() {
        return !revoque && expireLe.isAfter(Instant.now());
    }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public UUID getCalendrierId() { return calendrierId; }
    public void setCalendrierId(UUID calendrierId) { this.calendrierId = calendrierId; }
    public String getJeton() { return jeton; }
    public void setJeton(String jeton) { this.jeton = jeton; }
    public Instant getExpireLe() { return expireLe; }
    public void setExpireLe(Instant expireLe) { this.expireLe = expireLe; }
    public boolean isRevoque() { return revoque; }
    public void setRevoque(boolean revoque) { this.revoque = revoque; }
    public List<UUID> getPublications() { return publications; }
    public void setPublications(List<UUID> publications) { this.publications = publications; }
    public String getEmailDestinataire() { return emailDestinataire; }
    public void setEmailDestinataire(String emailDestinataire) { this.emailDestinataire = emailDestinataire; }
}
