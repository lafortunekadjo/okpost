package com.okpost.calendrier;

import com.okpost.commun.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Image générée par IA pour une publication. */
@Entity
@Table(name = "visuel_genere")
public class VisuelGenere extends BaseEntity {

    public enum Source { GENERE, TELEVERSE, MONTAGE, BANQUE }

    public enum TypeMedia { IMAGE, VIDEO }

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false)
    private Source source = Source.GENERE;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "type_media", nullable = false)
    private TypeMedia typeMedia = TypeMedia.IMAGE;

    @Column(name = "nom_origine")
    private String nomOrigine;

    private Long taille;

    /** Numéro de la diapositive d'un carrousel (1, 2, …) ; null pour un visuel unique ou un montage. */
    private Integer diapositive;

    public Integer getDiapositive() { return diapositive; }
    public void setDiapositive(Integer diapositive) { this.diapositive = diapositive; }

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "publication_id", nullable = false)
    private UUID publicationId;

    @Column(nullable = false, unique = true)
    private String chemin;

    @Column(nullable = false)
    private String mime;

    private String prompt;

    private String ratio;

    private String fournisseur;

    private String modele;

    /** Photo de la banque du client utilisée comme fond, le cas échéant. */
    @Column(name = "asset_id")
    private UUID assetId;

    public UUID getAssetId() { return assetId; }
    public void setAssetId(UUID assetId) { this.assetId = assetId; }
    public Source getSource() { return source; }
    public void setSource(Source source) { this.source = source; }
    public TypeMedia getTypeMedia() { return typeMedia; }
    public void setTypeMedia(TypeMedia typeMedia) { this.typeMedia = typeMedia; }
    public String getNomOrigine() { return nomOrigine; }
    public void setNomOrigine(String nomOrigine) { this.nomOrigine = nomOrigine; }
    public Long getTaille() { return taille; }
    public void setTaille(Long taille) { this.taille = taille; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public UUID getPublicationId() { return publicationId; }
    public void setPublicationId(UUID publicationId) { this.publicationId = publicationId; }
    public String getChemin() { return chemin; }
    public void setChemin(String chemin) { this.chemin = chemin; }
    public String getMime() { return mime; }
    public void setMime(String mime) { this.mime = mime; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getRatio() { return ratio; }
    public void setRatio(String ratio) { this.ratio = ratio; }
    public String getFournisseur() { return fournisseur; }
    public void setFournisseur(String fournisseur) { this.fournisseur = fournisseur; }
    public String getModele() { return modele; }
    public void setModele(String modele) { this.modele = modele; }
}
