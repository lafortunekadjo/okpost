package com.okpost.client;

import com.okpost.commun.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Fiche client : tout ce que l'IA doit savoir pour écrire au nom de la marque. */
@Entity
@Table(name = "client")
public class Client extends BaseEntity {

    /** photos : identifiants de photos de la banque d'images du client, pour que les publications montrent le vrai produit. */
    public record Offre(String nom, String description, String prix, String variantes, List<UUID> photos) {
        public Offre(String nom, String description, String prix) {
            this(nom, description, prix, null, null);
        }
    }

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "cm_id")
    private UUID cmId;

    @Column(nullable = false)
    private String nom;

    private String secteur;
    private String description;
    private String slogan;

    @Column(name = "annee_creation")
    private Integer anneeCreation;

    private String ville;
    private String pays;
    private String zone;
    private String adresse;
    private String horaires;

    @Column(nullable = false)
    private String langue = "fr";

    private String ton;
    private boolean tutoiement;

    @Column(name = "mots_a_eviter")
    private String motsAEviter;

    private String exemples;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<Offre> offres = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> cibles = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> concurrents = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> couleurs = new ArrayList<>();

    private String polices;
    private String domaine;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> reseaux = new LinkedHashMap<>();

    private String whatsapp;

    @Column(name = "type_activite", nullable = false)
    private String typeActivite = "SERVICES";
    private boolean archive;

    @Column(name = "auto_publication")
    private boolean autoPublication;

    @Column(name = "heure_publication", nullable = false)
    private String heurePublication = "18:00";

    @Column(nullable = false)
    private String fuseau = "Africa/Douala";

    @Column(name = "fetes_religieuses", nullable = false)
    private boolean fetesReligieuses = true;

    public boolean isFetesReligieuses() { return fetesReligieuses; }
    public void setFetesReligieuses(boolean fetesReligieuses) { this.fetesReligieuses = fetesReligieuses; }

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public UUID getCmId() { return cmId; }
    public void setCmId(UUID cmId) { this.cmId = cmId; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getSecteur() { return secteur; }
    public void setSecteur(String secteur) { this.secteur = secteur; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSlogan() { return slogan; }
    public void setSlogan(String slogan) { this.slogan = slogan; }
    public Integer getAnneeCreation() { return anneeCreation; }
    public void setAnneeCreation(Integer anneeCreation) { this.anneeCreation = anneeCreation; }
    public String getVille() { return ville; }
    public void setVille(String ville) { this.ville = ville; }
    public String getPays() { return pays; }
    public void setPays(String pays) { this.pays = pays; }
    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }
    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }
    public String getHoraires() { return horaires; }
    public void setHoraires(String horaires) { this.horaires = horaires; }
    public String getLangue() { return langue; }
    public void setLangue(String langue) { this.langue = langue; }
    public String getTon() { return ton; }
    public void setTon(String ton) { this.ton = ton; }
    public boolean isTutoiement() { return tutoiement; }
    public void setTutoiement(boolean tutoiement) { this.tutoiement = tutoiement; }
    public String getMotsAEviter() { return motsAEviter; }
    public void setMotsAEviter(String motsAEviter) { this.motsAEviter = motsAEviter; }
    public String getExemples() { return exemples; }
    public void setExemples(String exemples) { this.exemples = exemples; }
    public List<Offre> getOffres() { return offres; }
    public void setOffres(List<Offre> offres) { this.offres = offres; }
    public List<String> getCibles() { return cibles; }
    public void setCibles(List<String> cibles) { this.cibles = cibles; }
    public List<String> getConcurrents() { return concurrents; }
    public void setConcurrents(List<String> concurrents) { this.concurrents = concurrents; }
    public List<String> getCouleurs() { return couleurs; }
    public void setCouleurs(List<String> couleurs) { this.couleurs = couleurs; }
    public String getPolices() { return polices; }
    public void setPolices(String polices) { this.polices = polices; }
    public String getDomaine() { return domaine; }
    public void setDomaine(String domaine) { this.domaine = domaine; }
    public Map<String, String> getReseaux() { return reseaux; }
    public void setReseaux(Map<String, String> reseaux) { this.reseaux = reseaux; }
    public String getTypeActivite() { return typeActivite; }
    public void setTypeActivite(String typeActivite) { this.typeActivite = typeActivite; }
    /** Vend des produits (seuls ou avec des services) : visuels basés sur de vraies photos, commande par message. */
    public boolean vendDesProduits() { return "PRODUITS".equals(typeActivite) || "MIXTE".equals(typeActivite); }
    public String getWhatsapp() { return whatsapp; }
    public void setWhatsapp(String whatsapp) { this.whatsapp = whatsapp; }
    public boolean isArchive() { return archive; }
    public boolean isAutoPublication() { return autoPublication; }
    public void setAutoPublication(boolean autoPublication) { this.autoPublication = autoPublication; }
    public String getHeurePublication() { return heurePublication; }
    public void setHeurePublication(String heurePublication) { this.heurePublication = heurePublication; }
    public String getFuseau() { return fuseau; }
    public void setFuseau(String fuseau) { this.fuseau = fuseau; }
    public void setArchive(boolean archive) { this.archive = archive; }
}
