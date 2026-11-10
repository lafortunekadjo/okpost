package com.okpost.evenement;

import com.okpost.commun.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** Fête, journée mondiale ou temps fort du catalogue. Voir V5__evenements.sql pour les règles de date. */
@Entity
@Table(name = "evenement")
public class Evenement extends BaseEntity {

    public enum Categorie { NATIONAL, RELIGIEUX, COMMERCIAL, MONDIAL, SAISON }

    public enum Regle { FIXE, PAQUES, NIEME, DATE }

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Categorie categorie;

    @Column(nullable = false)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Regle regle;

    private Integer mois;
    private Integer jour;
    private Integer nieme;

    @Column(name = "jour_semaine")
    private Integer jourSemaine;

    @Column(nullable = false)
    private int decalage;

    @Column(name = "date_unique")
    private LocalDate dateUnique;

    @Column(name = "duree_jours", nullable = false)
    private int dureeJours = 1;

    @Column(nullable = false)
    private String secteurs = "";

    @Column(nullable = false)
    private String pays = "";

    private String angle;
    private boolean approximative;
    private boolean actif = true;

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public Categorie getCategorie() { return categorie; }
    public void setCategorie(Categorie categorie) { this.categorie = categorie; }
    public Regle getRegle() { return regle; }
    public void setRegle(Regle regle) { this.regle = regle; }
    public Integer getMois() { return mois; }
    public void setMois(Integer mois) { this.mois = mois; }
    public Integer getJour() { return jour; }
    public void setJour(Integer jour) { this.jour = jour; }
    public Integer getNieme() { return nieme; }
    public void setNieme(Integer nieme) { this.nieme = nieme; }
    public Integer getJourSemaine() { return jourSemaine; }
    public void setJourSemaine(Integer jourSemaine) { this.jourSemaine = jourSemaine; }
    public int getDecalage() { return decalage; }
    public void setDecalage(int decalage) { this.decalage = decalage; }
    public LocalDate getDateUnique() { return dateUnique; }
    public void setDateUnique(LocalDate dateUnique) { this.dateUnique = dateUnique; }
    public int getDureeJours() { return dureeJours; }
    public void setDureeJours(int dureeJours) { this.dureeJours = dureeJours; }
    public String getSecteurs() { return secteurs; }
    public void setSecteurs(String secteurs) { this.secteurs = secteurs == null ? "" : secteurs; }
    public String getPays() { return pays; }
    public void setPays(String pays) { this.pays = pays == null ? "" : pays; }
    public String getAngle() { return angle; }
    public void setAngle(String angle) { this.angle = angle; }
    public boolean isApproximative() { return approximative; }
    public void setApproximative(boolean approximative) { this.approximative = approximative; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
}
