package com.okpost.abonnement;

import com.okpost.commun.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "abonnement")
public class Abonnement extends BaseEntity {

    public enum Statut { ESSAI, ACTIF, GRACE, LECTURE_SEULE }

    public enum Mode { ESSAI, FLUTTERWAVE, NOTCHPAY, ESPECES }

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Formule formule;

    @Column(nullable = false)
    private LocalDate debut;

    @Column(nullable = false)
    private LocalDate fin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode;

    /** Statut réel à une date donnée : 5 jours de grâce après l'échéance, puis lecture seule. */
    public Statut statutAu(LocalDate jour) {
        return statutEffectif(statut, fin, jour);
    }

    public static Statut statutEffectif(Statut statut, LocalDate fin, LocalDate jour) {
        if (!jour.isAfter(fin)) {
            return statut == Statut.ESSAI ? Statut.ESSAI : Statut.ACTIF;
        }
        return jour.isAfter(fin.plusDays(5)) ? Statut.LECTURE_SEULE : Statut.GRACE;
    }

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public Formule getFormule() { return formule; }
    public void setFormule(Formule formule) { this.formule = formule; }
    public LocalDate getDebut() { return debut; }
    public void setDebut(LocalDate debut) { this.debut = debut; }
    public LocalDate getFin() { return fin; }
    public void setFin(LocalDate fin) { this.fin = fin; }
    public Statut getStatut() { return statut; }
    public void setStatut(Statut statut) { this.statut = statut; }
    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
}
