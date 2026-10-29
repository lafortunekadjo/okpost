package com.okpost.securite;

import com.okpost.commun.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Un espace de travail = une agence (ou un CM indépendant). */
@Entity
@Table(name = "workspace")
public class Workspace extends BaseEntity {

    @Column(nullable = false)
    private String nom;

    /** null = fournisseur IA par défaut de la plateforme. */
    @Column(name = "fournisseur_ia")
    private String fournisseurIa;

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getFournisseurIa() { return fournisseurIa; }
    public void setFournisseurIa(String fournisseurIa) { this.fournisseurIa = fournisseurIa; }
}
