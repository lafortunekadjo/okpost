package com.okpost.ia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Réglages modifiables depuis l'administration sans redéploiement (ex. ia.fournisseur). */
@Entity
@Table(name = "parametre_plateforme")
public class ParametrePlateforme {

    @Id
    private String cle;

    @Column(nullable = false)
    private String valeur;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ParametrePlateforme() {}

    public ParametrePlateforme(String cle, String valeur) {
        this.cle = cle;
        this.valeur = valeur;
    }

    public String getCle() { return cle; }
    public String getValeur() { return valeur; }

    public void setValeur(String valeur) {
        this.valeur = valeur;
        this.updatedAt = Instant.now();
    }
}
