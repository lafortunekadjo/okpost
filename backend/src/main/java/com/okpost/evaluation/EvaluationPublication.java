package com.okpost.evaluation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Notes de 1 à 5 données par le CM (ou un expert) à une publication générée. */
@Entity
@Table(name = "evaluation_publication")
public class EvaluationPublication {

    @Id
    @Column(name = "publication_id")
    private UUID publicationId;

    @Column(name = "calendrier_id", nullable = false)
    private UUID calendrierId;

    private Integer specificite;
    private Integer exactitude;
    private Integer contexte;
    private Integer faisabilite;
    private Integer pret;
    private String commentaire;

    @Column(name = "evaluateur_id")
    private UUID evaluateurId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void horodater() { updatedAt = Instant.now(); }

    public UUID getPublicationId() { return publicationId; }
    public void setPublicationId(UUID publicationId) { this.publicationId = publicationId; }
    public UUID getCalendrierId() { return calendrierId; }
    public void setCalendrierId(UUID calendrierId) { this.calendrierId = calendrierId; }
    public Integer getSpecificite() { return specificite; }
    public void setSpecificite(Integer specificite) { this.specificite = specificite; }
    public Integer getExactitude() { return exactitude; }
    public void setExactitude(Integer exactitude) { this.exactitude = exactitude; }
    public Integer getContexte() { return contexte; }
    public void setContexte(Integer contexte) { this.contexte = contexte; }
    public Integer getFaisabilite() { return faisabilite; }
    public void setFaisabilite(Integer faisabilite) { this.faisabilite = faisabilite; }
    public Integer getPret() { return pret; }
    public void setPret(Integer pret) { this.pret = pret; }
    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }
    public UUID getEvaluateurId() { return evaluateurId; }
    public void setEvaluateurId(UUID evaluateurId) { this.evaluateurId = evaluateurId; }
    public Instant getUpdatedAt() { return updatedAt; }
}
