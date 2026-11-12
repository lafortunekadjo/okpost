package com.okpost.client;

import com.okpost.commun.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "client_asset")
public class ClientAsset extends BaseEntity {

    public enum Type { LOGO_COULEUR, LOGO_BLANC, LOGO_NOIR, ICONE, PHOTO }

    /** État de la description automatique d'une photo de la banque. */
    public enum Analyse { AUCUNE, EN_COURS, OK, ECHEC }

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

    /** Ce que montre la photo, en une phrase (décrit par l'IA ou saisi). */
    private String description;

    /** Mots-clés français et anglais, pour retrouver la photo selon le sujet d'une publication. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> tags = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "analyse_statut", nullable = false)
    private Analyse analyse = Analyse.AUCUNE;

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
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags); }
    public Analyse getAnalyse() { return analyse; }
    public void setAnalyse(Analyse analyse) { this.analyse = analyse; }
}
