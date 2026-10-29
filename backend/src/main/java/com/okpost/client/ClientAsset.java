package com.okpost.client;

import com.okpost.commun.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "client_asset")
public class ClientAsset extends BaseEntity {

    public enum Type { LOGO_COULEUR, LOGO_BLANC, LOGO_NOIR, ICONE, PHOTO }

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false)
    private String chemin;

    @Column(name = "nom_origine")
    private String nomOrigine;

    @Column(nullable = false)
    private String mime;

    @Column(nullable = false)
    private long taille;

    private String legende;

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public String getChemin() { return chemin; }
    public void setChemin(String chemin) { this.chemin = chemin; }
    public String getNomOrigine() { return nomOrigine; }
    public void setNomOrigine(String nomOrigine) { this.nomOrigine = nomOrigine; }
    public String getMime() { return mime; }
    public void setMime(String mime) { this.mime = mime; }
    public long getTaille() { return taille; }
    public void setTaille(long taille) { this.taille = taille; }
    public String getLegende() { return legende; }
    public void setLegende(String legende) { this.legende = legende; }
}
