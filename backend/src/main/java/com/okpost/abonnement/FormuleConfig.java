package com.okpost.abonnement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Prix et limites d'une formule, modifiables par le super-admin. Le code est celui de l'énumération Formule. */
@Entity
@Table(name = "formule_config")
public class FormuleConfig {

    @Id
    private String code;

    @Column(nullable = false)
    private String nom;

    @Column(name = "prix_mensuel_xaf", nullable = false)
    private long prixMensuelXaf;

    @Column(name = "max_clients", nullable = false)
    private int maxClients;

    @Column(name = "max_membres", nullable = false)
    private int maxMembres;

    @Column(name = "calendriers_par_mois", nullable = false)
    private int calendriersParMois;

    @Column(name = "images_par_mois", nullable = false)
    private int imagesParMois;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public long getPrixMensuelXaf() { return prixMensuelXaf; }
    public void setPrixMensuelXaf(long prixMensuelXaf) { this.prixMensuelXaf = prixMensuelXaf; }
    public int getMaxClients() { return maxClients; }
    public void setMaxClients(int maxClients) { this.maxClients = maxClients; }
    public int getMaxMembres() { return maxMembres; }
    public void setMaxMembres(int maxMembres) { this.maxMembres = maxMembres; }
    public int getCalendriersParMois() { return calendriersParMois; }
    public void setCalendriersParMois(int calendriersParMois) { this.calendriersParMois = calendriersParMois; }
    public int getImagesParMois() { return imagesParMois; }
    public void setImagesParMois(int imagesParMois) { this.imagesParMois = imagesParMois; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
