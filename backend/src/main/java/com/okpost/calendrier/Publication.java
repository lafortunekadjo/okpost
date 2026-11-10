package com.okpost.calendrier;

import com.okpost.commun.BaseEntity;
import com.okpost.ia.Modeles.BriefVisuel;
import com.okpost.ia.Modeles.ScenarioVideo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "publication")
public class Publication extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "calendrier_id", nullable = false)
    private UUID calendrierId;

    @Column(name = "date_prevue", nullable = false)
    private LocalDate datePrevue;

    @Column(nullable = false)
    private String reseau;

    @Column(nullable = false)
    private String format;

    private String pilier;

    @Column(nullable = false)
    private String idee;

    private String objectif;

    @Column(name = "temps_fort")
    private String tempsFort;

    private String legende;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> hashtags = new ArrayList<>();

    private String cta;

    @JdbcTypeCode(SqlTypes.JSON)
    private BriefVisuel brief;

    @JdbcTypeCode(SqlTypes.JSON)
    private ScenarioVideo scenario;

    @Column(name = "detail_genere")
    private boolean detailGenere;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutPublication statut = StatutPublication.IDEE;

    @Version
    private int version;

    @Column(name = "publication_prevue")
    private java.time.Instant publicationPrevue;

    @Column(name = "publiee_le")
    private java.time.Instant publieeLe;

    @Column(name = "post_externe_id")
    private String postExterneId;

    @Column(name = "lien_post")
    private String lienPost;

    @Column(name = "erreur_publication")
    private String erreurPublication;

    private int tentatives;

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public UUID getCalendrierId() { return calendrierId; }
    public void setCalendrierId(UUID calendrierId) { this.calendrierId = calendrierId; }
    public LocalDate getDatePrevue() { return datePrevue; }
    public void setDatePrevue(LocalDate datePrevue) { this.datePrevue = datePrevue; }
    public String getReseau() { return reseau; }
    public void setReseau(String reseau) { this.reseau = reseau; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getPilier() { return pilier; }
    public void setPilier(String pilier) { this.pilier = pilier; }
    public String getIdee() { return idee; }
    public void setIdee(String idee) { this.idee = idee; }
    public String getObjectif() { return objectif; }
    public void setObjectif(String objectif) { this.objectif = objectif; }
    public String getTempsFort() { return tempsFort; }
    public void setTempsFort(String tempsFort) { this.tempsFort = tempsFort; }
    public String getLegende() { return legende; }
    public void setLegende(String legende) { this.legende = legende; }
    public List<String> getHashtags() { return hashtags; }
    public void setHashtags(List<String> hashtags) { this.hashtags = hashtags; }
    public String getCta() { return cta; }
    public void setCta(String cta) { this.cta = cta; }
    public BriefVisuel getBrief() { return brief; }
    public void setBrief(BriefVisuel brief) { this.brief = brief; }
    public ScenarioVideo getScenario() { return scenario; }
    public void setScenario(ScenarioVideo scenario) { this.scenario = scenario; }
    public boolean isDetailGenere() { return detailGenere; }
    public void setDetailGenere(boolean detailGenere) { this.detailGenere = detailGenere; }
    public StatutPublication getStatut() { return statut; }
    public void setStatut(StatutPublication statut) { this.statut = statut; }
    public int getVersion() { return version; }
    public java.time.Instant getPublicationPrevue() { return publicationPrevue; }
    public void setPublicationPrevue(java.time.Instant publicationPrevue) { this.publicationPrevue = publicationPrevue; }
    public java.time.Instant getPublieeLe() { return publieeLe; }
    public void setPublieeLe(java.time.Instant publieeLe) { this.publieeLe = publieeLe; }
    public String getPostExterneId() { return postExterneId; }
    public void setPostExterneId(String postExterneId) { this.postExterneId = postExterneId; }
    public String getLienPost() { return lienPost; }
    public void setLienPost(String lienPost) { this.lienPost = lienPost; }
    @Column(name = "mode_carrousel")
    private String modeCarrousel;

    public String getModeCarrousel() { return modeCarrousel; }
    public void setModeCarrousel(String modeCarrousel) { this.modeCarrousel = modeCarrousel; }

    public String getErreurPublication() { return erreurPublication; }
    public void setErreurPublication(String erreurPublication) { this.erreurPublication = erreurPublication; }
    public int getTentatives() { return tentatives; }
    public void setTentatives(int tentatives) { this.tentatives = tentatives; }
}
