package com.okpost.validation;

import com.okpost.commun.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "retour_client")
public class RetourClient extends BaseEntity {

    public enum Decision { OK, A_MODIFIER, MODIFIEE }

    @Column(name = "publication_id", nullable = false)
    private UUID publicationId;

    @Column(name = "lien_id")
    private UUID lienId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Decision decision;

    private String commentaire;

    @Column(name = "legende_proposee")
    private String legendeProposee;

    @Column(name = "legende_origine")
    private String legendeOrigine;

    @Column(name = "ip_hash")
    private String ipHash;

    @Column(name = "contact_id")
    private UUID contactId;

    @Column(name = "nom_validateur")
    private String nomValidateur;

    public UUID getPublicationId() { return publicationId; }
    public void setPublicationId(UUID publicationId) { this.publicationId = publicationId; }
    public UUID getLienId() { return lienId; }
    public void setLienId(UUID lienId) { this.lienId = lienId; }
    public Decision getDecision() { return decision; }
    public void setDecision(Decision decision) { this.decision = decision; }
    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }
    public String getLegendeProposee() { return legendeProposee; }
    public void setLegendeProposee(String legendeProposee) { this.legendeProposee = legendeProposee; }
    public String getLegendeOrigine() { return legendeOrigine; }
    public void setLegendeOrigine(String legendeOrigine) { this.legendeOrigine = legendeOrigine; }
    public UUID getContactId() { return contactId; }
    public void setContactId(UUID contactId) { this.contactId = contactId; }
    public String getNomValidateur() { return nomValidateur; }
    public void setNomValidateur(String nomValidateur) { this.nomValidateur = nomValidateur; }
    public String getIpHash() { return ipHash; }
    public void setIpHash(String ipHash) { this.ipHash = ipHash; }
}
