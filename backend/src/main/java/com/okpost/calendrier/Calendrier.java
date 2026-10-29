package com.okpost.calendrier;

import com.okpost.commun.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "calendrier")
public class Calendrier extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "periode_debut", nullable = false)
    private LocalDate periodeDebut;

    @Column(name = "periode_fin", nullable = false)
    private LocalDate periodeFin;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> reseaux = new ArrayList<>();

    /** Nombre de publications par semaine et par réseau. */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Integer> rythme = new LinkedHashMap<>();

    private String objectifs;

    @Column(name = "temps_forts")
    private String tempsForts;

    @Column(nullable = false)
    private String langue = "fr";

    private String strategie;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> piliers = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_generation", nullable = false)
    private StatutGeneration statutGeneration = StatutGeneration.EN_ATTENTE;

    @Column(name = "message_erreur")
    private String messageErreur;

    @Column(name = "fournisseur_ia")
    private String fournisseurIa;

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    public LocalDate getPeriodeDebut() { return periodeDebut; }
    public void setPeriodeDebut(LocalDate periodeDebut) { this.periodeDebut = periodeDebut; }
    public LocalDate getPeriodeFin() { return periodeFin; }
    public void setPeriodeFin(LocalDate periodeFin) { this.periodeFin = periodeFin; }
    public List<String> getReseaux() { return reseaux; }
    public void setReseaux(List<String> reseaux) { this.reseaux = reseaux; }
    public Map<String, Integer> getRythme() { return rythme; }
    public void setRythme(Map<String, Integer> rythme) { this.rythme = rythme; }
    public String getObjectifs() { return objectifs; }
    public void setObjectifs(String objectifs) { this.objectifs = objectifs; }
    public String getTempsForts() { return tempsForts; }
    public void setTempsForts(String tempsForts) { this.tempsForts = tempsForts; }
    public String getLangue() { return langue; }
    public void setLangue(String langue) { this.langue = langue; }
    public String getStrategie() { return strategie; }
    public void setStrategie(String strategie) { this.strategie = strategie; }
    public List<String> getPiliers() { return piliers; }
    public void setPiliers(List<String> piliers) { this.piliers = piliers; }
    public StatutGeneration getStatutGeneration() { return statutGeneration; }
    public void setStatutGeneration(StatutGeneration statutGeneration) { this.statutGeneration = statutGeneration; }
    public String getMessageErreur() { return messageErreur; }
    public void setMessageErreur(String messageErreur) { this.messageErreur = messageErreur; }
    public String getFournisseurIa() { return fournisseurIa; }
    public void setFournisseurIa(String fournisseurIa) { this.fournisseurIa = fournisseurIa; }
}
