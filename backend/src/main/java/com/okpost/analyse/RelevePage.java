package com.okpost.analyse;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/** Nombre d'abonnés d'une Page relevé un jour donné (l'API ne donne pas l'historique). */
@Entity
@Table(name = "releve_page")
public class RelevePage {

    @Id
    private UUID id;

    @Column(name = "compte_id", nullable = false)
    private UUID compteId;

    @Column(nullable = false)
    private LocalDate jour;

    private Integer abonnes;

    @PrePersist
    void avantCreation() { if (id == null) id = UUID.randomUUID(); }

    public UUID getId() { return id; }
    public UUID getCompteId() { return compteId; }
    public void setCompteId(UUID compteId) { this.compteId = compteId; }
    public LocalDate getJour() { return jour; }
    public void setJour(LocalDate jour) { this.jour = jour; }
    public Integer getAbonnes() { return abonnes; }
    public void setAbonnes(Integer abonnes) { this.abonnes = abonnes; }
}
