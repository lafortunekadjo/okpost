package com.okpost.admin;

import com.okpost.abonnement.Abonnement;
import com.okpost.abonnement.AbonnementRepository;
import com.okpost.abonnement.Formule;
import com.okpost.abonnement.FormuleConfig;
import com.okpost.abonnement.FormuleConfigRepository;
import com.okpost.abonnement.FormulesService;
import com.okpost.admin.AdminService.Compte;
import com.okpost.commun.ApiException;
import com.okpost.securite.Role;
import com.okpost.securite.Session;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdministrationAbonnementsTest {

    private final FormuleConfigRepository depotFormules = mock(FormuleConfigRepository.class);
    private final JournalService journal = mock(JournalService.class);
    private final FormulesService formules = new FormulesService(depotFormules, journal);
    private final AbonnementRepository abonnements = mock(AbonnementRepository.class);
    private final AdminService admin = new AdminService(mock(JdbcTemplate.class), abonnements, formules, journal);
    private final Session superAdmin = new Session(UUID.randomUUID(), UUID.randomUUID(), Role.SUPER_ADMIN);
    private final LocalDate aujourdhui = LocalDate.of(2026, 11, 26);

    // ------------------------------------------------------------ formules

    @Test
    void sansLigneEnBaseLesValeursDOrigineS_appliquent() {
        when(depotFormules.findById(anyString())).thenReturn(Optional.empty());
        var pro = formules.pour(Formule.PRO);
        assertThat(pro.maxClients()).isEqualTo(10);
        assertThat(pro.prixMensuelXaf()).isEqualTo(15_000);
        assertThat(pro.nom()).isEqualTo("Pro");
        assertThat(formules.toutes()).hasSize(4);
    }

    @Test
    void uneFormuleModifieeEstLueEnBaseEtJournalisee() {
        FormuleConfig c = new FormuleConfig();
        c.setCode("SOLO");
        c.setNom("Solo");
        c.setPrixMensuelXaf(7500);
        c.setMaxClients(3);
        c.setMaxMembres(1);
        c.setCalendriersParMois(3);
        c.setImagesParMois(30);
        when(depotFormules.findById("SOLO")).thenReturn(Optional.of(c));
        var apres = formules.modifier(superAdmin, Formule.SOLO, new FormulesService.Modification(" Solo+ ", 9000, 5, 2, 8, 60));
        assertThat(apres.nom()).isEqualTo("Solo+");
        assertThat(apres.prixMensuelXaf()).isEqualTo(9000);
        assertThat(apres.maxClients()).isEqualTo(5);
        assertThat(c.getImagesParMois()).isEqualTo(60);
        verify(depotFormules).save(c);
        verify(journal).enregistrer(any(Session.class), anyString(), anyString(), anyString());
        assertThat(formules.pour(Formule.SOLO).calendriersParMois()).isEqualTo(8);
    }

    @Test
    void lEssaiResteGratuitQuoiQuOnSaisisse() {
        when(depotFormules.findById(anyString())).thenReturn(Optional.empty());
        var essai = formules.modifier(superAdmin, Formule.ESSAI, new FormulesService.Modification("Essai", 5000, 1, 1, 1, 5));
        assertThat(essai.prixMensuelXaf()).isZero();
    }

    @Test
    void lesValeursAbsurdesEtLesNonSuperAdminSontRefuses() {
        when(depotFormules.findById(anyString())).thenReturn(Optional.empty());
        for (FormulesService.Modification m : List.of(
                new FormulesService.Modification(" ", 1000, 1, 1, 1, 1),
                new FormulesService.Modification("Pro", -1, 1, 1, 1, 1),
                new FormulesService.Modification("Pro", 1000, 0, 1, 1, 1),
                new FormulesService.Modification("Pro", 1000, 1, 1000, 1, 1),
                new FormulesService.Modification("Pro", 1000, 1, 1, 0, 1),
                new FormulesService.Modification("Pro", 1000, 1, 1, 1, -5))) {
            assertThatThrownBy(() -> formules.modifier(superAdmin, Formule.PRO, m)).isInstanceOf(ApiException.class);
        }
        var admin = new Session(UUID.randomUUID(), UUID.randomUUID(), Role.ADMIN);
        assertThatThrownBy(() -> formules.modifier(admin, Formule.PRO, new FormulesService.Modification("Pro", 1000, 1, 1, 1, 1)))
                .isInstanceOf(ApiException.class);
        verify(depotFormules, never()).save(any());
    }

    // ------------------------------------------------------------ changement d'abonnement d'un compte

    private Abonnement abonnement(Formule f, LocalDate fin, Abonnement.Mode mode) {
        Abonnement a = new Abonnement();
        a.setFormule(f);
        a.setDebut(aujourdhui.minusDays(10));
        a.setFin(fin);
        a.setStatut(f == Formule.ESSAI ? Abonnement.Statut.ESSAI : Abonnement.Statut.ACTIF);
        a.setMode(mode);
        return a;
    }

    @Test
    void attribuerUneFormuleModifieLAbonnementCourantSurPlaceEtLeJournalise() {
        UUID espace = UUID.randomUUID();
        Abonnement a = abonnement(Formule.ESSAI, aujourdhui.plusDays(3), Abonnement.Mode.ESSAI);
        when(abonnements.findFirstByWorkspaceIdOrderByFinDesc(espace)).thenReturn(Optional.of(a));
        Abonnement r = admin.changerAbonnement(superAdmin, espace, Formule.PRO, LocalDate.now().plusDays(30));
        assertThat(r.getFormule()).isEqualTo(Formule.PRO);
        assertThat(r.getStatut()).isEqualTo(Abonnement.Statut.ACTIF);
        assertThat(r.getMode()).isEqualTo(Abonnement.Mode.ESPECES);
        assertThat(r.getDebut()).isEqualTo(LocalDate.now());
        verify(abonnements).save(a);
        verify(journal).enregistrer(any(UUID.class), any(UUID.class), any(), anyString(), anyString(), anyString());
    }

    @Test
    void unPaiementEnLigneResteUnPaiementEnLigneEtUneProlongationGardeLeDebut() {
        UUID espace = UUID.randomUUID();
        Abonnement a = abonnement(Formule.PRO, aujourdhui.plusDays(2), Abonnement.Mode.FLUTTERWAVE);
        when(abonnements.findFirstByWorkspaceIdOrderByFinDesc(espace)).thenReturn(Optional.of(a));
        Abonnement r = admin.changerAbonnement(superAdmin, espace, Formule.PRO, LocalDate.now().plusDays(60));
        assertThat(r.getMode()).isEqualTo(Abonnement.Mode.FLUTTERWAVE);
        assertThat(r.getDebut()).isEqualTo(aujourdhui.minusDays(10));
    }

    @Test
    void repasserEnEssaiRemetLeStatutEtLeMode() {
        UUID espace = UUID.randomUUID();
        Abonnement a = abonnement(Formule.PRO, aujourdhui.plusDays(20), Abonnement.Mode.ESPECES);
        when(abonnements.findFirstByWorkspaceIdOrderByFinDesc(espace)).thenReturn(Optional.of(a));
        Abonnement r = admin.changerAbonnement(superAdmin, espace, Formule.ESSAI, LocalDate.now().plusDays(14));
        assertThat(r.getStatut()).isEqualTo(Abonnement.Statut.ESSAI);
        assertThat(r.getMode()).isEqualTo(Abonnement.Mode.ESSAI);
    }

    @Test
    void lesChangementsInvalidesOuNonAutorisesSontRefuses() {
        UUID espace = UUID.randomUUID();
        when(abonnements.findFirstByWorkspaceIdOrderByFinDesc(espace)).thenReturn(Optional.of(abonnement(Formule.SOLO, aujourdhui, Abonnement.Mode.ESPECES)));
        assertThatThrownBy(() -> admin.changerAbonnement(superAdmin, espace, Formule.PRO, LocalDate.of(1999, 1, 1))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> admin.changerAbonnement(superAdmin, espace, Formule.PRO, LocalDate.now().plusYears(500))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> admin.changerAbonnement(superAdmin, espace, null, LocalDate.now())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> admin.changerAbonnement(new Session(UUID.randomUUID(), espace, Role.ADMIN), espace, Formule.PRO, LocalDate.now()))
                .isInstanceOf(ApiException.class);
        UUID inconnu = UUID.randomUUID();
        when(abonnements.findFirstByWorkspaceIdOrderByFinDesc(inconnu)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> admin.changerAbonnement(superAdmin, inconnu, Formule.PRO, LocalDate.now())).isInstanceOf(ApiException.class);
        verify(abonnements, never()).save(any());
    }

    // ------------------------------------------------------------ suivi des comptes

    private Compte compte(String formule, String statut, LocalDate fin, int clients, int calendriersMois, int images, boolean interne) {
        when(depotFormules.findById(anyString())).thenReturn(Optional.empty());
        return admin.compte(UUID.randomUUID(), "Agence", null, "a@b.cm", 1, clients, 4, calendriersMois, 10, 2, images, 5, formule, statut, fin,
                "ESPECES", interne, null, aujourdhui);
    }

    @Test
    void leStatutReelLesJoursRestantsEtLeUsageSontCalcules() {
        Compte c = compte("PRO", "ACTIF", aujourdhui.plusDays(5), 8, 2, 40, false);
        assertThat(c.statutAbonnement()).isEqualTo("ACTIF");
        assertThat(c.joursRestants()).isEqualTo(5);
        assertThat(c.maxClients()).isEqualTo(10);
        assertThat(c.maxImages()).isEqualTo(100);
        assertThat(c.procheLimite()).as("8 clients sur 10").isTrue();
        assertThat(compte("PRO", "ACTIF", aujourdhui.plusDays(5), 2, 2, 40, false).procheLimite()).isFalse();
        // 5 jours de grâce après l'échéance, puis lecture seule
        assertThat(compte("PRO", "ACTIF", aujourdhui.minusDays(2), 1, 1, 1, false).statutAbonnement()).isEqualTo("GRACE");
        assertThat(compte("PRO", "ACTIF", aujourdhui.minusDays(9), 1, 1, 1, false).statutAbonnement()).isEqualTo("LECTURE_SEULE");
        assertThat(compte("ESSAI", "ESSAI", aujourdhui.plusDays(9), 1, 1, 1, false).procheLimite()).as("1 client sur 1").isTrue();
        // Un compte sans abonnement ne casse rien
        Compte sans = admin.compte(UUID.randomUUID(), "X", null, null, 0, 0, 0, 0, 0, 0, 0, 0, null, null, null, null, false, null, aujourdhui);
        assertThat(sans.statutAbonnement()).isNull();
        assertThat(sans.procheLimite()).isFalse();
    }

    @Test
    void lesTotauxCompterontEssaisPayantsRevenuEtEcheancesSansLEspaceDuProprietaire() {
        when(depotFormules.findById(anyString())).thenReturn(Optional.empty());
        List<Compte> comptes = List.of(
                compte("ESSAI", "ESSAI", aujourdhui.plusDays(3), 1, 1, 1, false),      // essai qui expire bientôt
                compte("ESSAI", "ESSAI", aujourdhui.plusDays(12), 0, 0, 0, false),     // essai
                compte("SOLO", "ACTIF", aujourdhui.plusDays(20), 1, 1, 1, false),      // payant : 7 500
                compte("PRO", "ACTIF", aujourdhui.plusDays(6), 1, 1, 1, false),        // payant : 15 000, expire bientôt
                compte("PRO", "ACTIF", aujourdhui.minusDays(3), 1, 1, 1, false),       // en grâce : pas de revenu
                compte("PRO", "ACTIF", aujourdhui.minusDays(30), 1, 1, 1, false),      // lecture seule
                compte("AGENCE", "ACTIF", aujourdhui.plusYears(50), 1, 1, 1, true));   // l'espace du propriétaire : ignoré
        var t = admin.totaux(7, 6, 6, 60, 3, 9, 1, 12, 0, comptes);
        assertThat(t.essais()).isEqualTo(2);
        assertThat(t.payants()).isEqualTo(2);
        assertThat(t.revenuMensuelXaf()).isEqualTo(22_500);
        assertThat(t.expirentBientot()).isEqualTo(2);
        assertThat(t.expires()).isEqualTo(2);
    }
}
