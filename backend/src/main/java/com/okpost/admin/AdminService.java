package com.okpost.admin;

import com.okpost.abonnement.Abonnement;
import com.okpost.abonnement.AbonnementRepository;
import com.okpost.abonnement.Formule;
import com.okpost.abonnement.FormulesService;
import com.okpost.commun.ApiException;
import com.okpost.securite.Session;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Vue d'ensemble de la plateforme pour le super-admin : comptes, abonnements, usage, consommation d'IA, formules et journal d'activité. */
@Service
public class AdminService {

    /**
     * Un compte (espace de travail) avec son abonnement et son usage du mois face aux limites de sa formule.
     * interne : l'espace du propriétaire de la plateforme, exclu des revenus et des essais.
     */
    public record Compte(UUID id, String nom, Instant creeLe, String emailAdmin, int membres, int clients, int calendriers, int calendriersMois,
                         int publications, int publiees, int imagesMois, int appelsIaMois,
                         String formule, String nomFormule, String statutAbonnement, LocalDate finAbonnement, Long joursRestants, String modeAbonnement,
                         int maxClients, int maxMembres, int maxCalendriers, int maxImages, boolean procheLimite, boolean interne,
                         Instant derniereConnexion) {}

    public record Totaux(int comptes, int clients, int calendriers, int publications, int publieesMois, int appelsIaMois,
                         int echecsIaMois, int imagesMois, int echecsPublication7j,
                         int essais, int payants, long revenuMensuelXaf, int expirentBientot, int expires) {}

    public record VueComptes(Totaux totaux, List<Compte> comptes) {}

    public record FormuleAdmin(String code, String nom, long prixMensuelXaf, int maxClients, int maxMembres, int calendriersParMois,
                               int imagesParMois, int comptes) {}

    public record Ligne(Instant date, String action, String libelle, String espace, UUID espaceId, String auteur, String cible, String detail) {}

    public record PageJournal(long total, int page, int taille, List<Ligne> lignes, Map<String, String> actions) {}

    /** Seuil à partir duquel un compte est signalé « proche d'une limite » (usage / limite). */
    static final double SEUIL_LIMITE = 0.8;

    private final JdbcTemplate jdbc;
    private final AbonnementRepository abonnements;
    private final FormulesService formules;
    private final JournalService journal;

    public AdminService(JdbcTemplate jdbc, AbonnementRepository abonnements, FormulesService formules, JournalService journal) {
        this.jdbc = jdbc;
        this.abonnements = abonnements;
        this.formules = formules;
        this.journal = journal;
    }

    @Transactional(readOnly = true)
    public VueComptes comptes() {
        LocalDate aujourdhui = LocalDate.now();
        List<Compte> comptes = jdbc.query("""
                SELECT w.id, w.nom, w.created_at,
                  (SELECT u.email FROM app_user u WHERE u.workspace_id = w.id ORDER BY u.created_at LIMIT 1) AS email_admin,
                  (SELECT count(*) FROM app_user u WHERE u.workspace_id = w.id AND u.actif) AS membres,
                  (SELECT count(*) FROM client c WHERE c.workspace_id = w.id AND NOT c.archive) AS clients,
                  (SELECT count(*) FROM calendrier k WHERE k.workspace_id = w.id) AS calendriers,
                  (SELECT count(*) FROM calendrier k WHERE k.workspace_id = w.id AND k.created_at >= date_trunc('month', now())) AS calendriers_mois,
                  (SELECT count(*) FROM publication p WHERE p.workspace_id = w.id) AS publications,
                  (SELECT count(*) FROM publication p WHERE p.workspace_id = w.id AND p.statut = 'PUBLIEE') AS publiees,
                  (SELECT count(*) FROM visuel_genere v WHERE v.workspace_id = w.id AND v.source = 'GENERE'
                      AND v.created_at >= date_trunc('month', now())) AS images_mois,
                  (SELECT count(*) FROM journal_ia j WHERE j.workspace_id = w.id
                      AND j.created_at >= date_trunc('month', now())) AS appels_ia_mois,
                  (SELECT a.formule FROM abonnement a WHERE a.workspace_id = w.id ORDER BY a.fin DESC LIMIT 1) AS formule,
                  (SELECT a.statut FROM abonnement a WHERE a.workspace_id = w.id ORDER BY a.fin DESC LIMIT 1) AS statut_abo,
                  (SELECT a.fin FROM abonnement a WHERE a.workspace_id = w.id ORDER BY a.fin DESC LIMIT 1) AS fin_abo,
                  (SELECT a.mode FROM abonnement a WHERE a.workspace_id = w.id ORDER BY a.fin DESC LIMIT 1) AS mode_abo,
                  (SELECT max(j.created_at) FROM journal_activite j WHERE j.workspace_id = w.id AND j.action = 'CONNEXION') AS derniere_connexion,
                  EXISTS (SELECT 1 FROM app_user u WHERE u.workspace_id = w.id AND u.role = 'SUPER_ADMIN') AS interne
                FROM workspace w
                ORDER BY w.created_at DESC
                """, (rs, i) -> compte(rs.getObject("id", UUID.class), rs.getString("nom"), instant(rs.getTimestamp("created_at")),
                rs.getString("email_admin"), rs.getInt("membres"), rs.getInt("clients"), rs.getInt("calendriers"), rs.getInt("calendriers_mois"),
                rs.getInt("publications"), rs.getInt("publiees"), rs.getInt("images_mois"), rs.getInt("appels_ia_mois"),
                rs.getString("formule"), rs.getString("statut_abo"), rs.getObject("fin_abo", LocalDate.class), rs.getString("mode_abo"),
                rs.getBoolean("interne"), instant(rs.getTimestamp("derniere_connexion")), aujourdhui));

        Totaux totaux = jdbc.queryForObject("""
                SELECT
                  (SELECT count(*) FROM workspace) AS comptes,
                  (SELECT count(*) FROM client WHERE NOT archive) AS clients,
                  (SELECT count(*) FROM calendrier) AS calendriers,
                  (SELECT count(*) FROM publication) AS publications,
                  (SELECT count(*) FROM publication WHERE statut = 'PUBLIEE' AND publiee_le >= date_trunc('month', now())) AS publiees_mois,
                  (SELECT count(*) FROM journal_ia WHERE created_at >= date_trunc('month', now())) AS appels_ia,
                  (SELECT count(*) FROM journal_ia WHERE NOT succes AND created_at >= date_trunc('month', now())) AS echecs_ia,
                  (SELECT count(*) FROM visuel_genere WHERE source = 'GENERE' AND created_at >= date_trunc('month', now())) AS images,
                  (SELECT count(*) FROM journal_activite WHERE action = 'PUBLICATION_ECHEC' AND created_at >= now() - interval '7 days') AS echecs_pub
                """, (rs, i) -> totaux(rs.getInt("comptes"), rs.getInt("clients"), rs.getInt("calendriers"), rs.getInt("publications"),
                rs.getInt("publiees_mois"), rs.getInt("appels_ia"), rs.getInt("echecs_ia"), rs.getInt("images"), rs.getInt("echecs_pub"), comptes));
        return new VueComptes(totaux, comptes);
    }

    /** Assemble un compte : statut réel de l'abonnement, jours restants et usage face aux limites de la formule. */
    Compte compte(UUID id, String nom, Instant creeLe, String emailAdmin, int membres, int clients, int calendriers, int calendriersMois,
                  int publications, int publiees, int imagesMois, int appelsIa, String codeFormule, String statut, LocalDate fin, String mode,
                  boolean interne, Instant derniereConnexion, LocalDate aujourdhui) {
        Formule f = formule(codeFormule);
        FormulesService.Limites l = f == null ? null : formules.pour(f);
        String statutEffectif = null;
        Long jours = null;
        if (fin != null && statut != null) {
            statutEffectif = Abonnement.statutEffectif(Abonnement.Statut.valueOf(statut), fin, aujourdhui).name();
            jours = ChronoUnit.DAYS.between(aujourdhui, fin);
        }
        int maxClients = l == null ? 0 : l.maxClients();
        int maxMembres = l == null ? 0 : l.maxMembres();
        int maxCalendriers = l == null ? 0 : l.calendriersParMois();
        int maxImages = l == null ? 0 : l.imagesParMois();
        boolean proche = l != null && (proche(clients, maxClients) || proche(membres, maxMembres)
                || proche(calendriersMois, maxCalendriers) || proche(imagesMois, maxImages));
        return new Compte(id, nom, creeLe, emailAdmin, membres, clients, calendriers, calendriersMois, publications, publiees, imagesMois, appelsIa,
                codeFormule, l == null ? null : l.nom(), statutEffectif, fin, jours, mode, maxClients, maxMembres, maxCalendriers, maxImages, proche,
                interne, derniereConnexion);
    }

    static boolean proche(int usage, int limite) {
        return limite > 0 && usage >= Math.ceil(limite * SEUIL_LIMITE);
    }

    private static Formule formule(String code) {
        try {
            return code == null ? null : Formule.valueOf(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Essais en cours, abonnés payants et revenu mensuel estimé : l'espace du propriétaire de la plateforme n'est jamais compté. */
    Totaux totaux(int comptesTotal, int clients, int calendriers, int publications, int publieesMois, int appelsIa, int echecsIa, int images,
                  int echecsPub, List<Compte> comptes) {
        int essais = 0;
        int payants = 0;
        long revenu = 0;
        int expirent = 0;
        int expires = 0;
        for (Compte c : comptes) {
            if (c.interne() || c.statutAbonnement() == null) continue;
            boolean paye = !"ESSAI".equals(c.formule());
            switch (c.statutAbonnement()) {
                case "ESSAI" -> essais++;
                case "ACTIF" -> {
                    if (paye) {
                        payants++;
                        revenu += formules.pour(Formule.valueOf(c.formule())).prixMensuelXaf();
                    }
                }
                default -> expires++;
            }
            if (("ESSAI".equals(c.statutAbonnement()) || "ACTIF".equals(c.statutAbonnement())) && c.joursRestants() != null
                    && c.joursRestants() >= 0 && c.joursRestants() <= 7) {
                expirent++;
            }
        }
        return new Totaux(comptesTotal, clients, calendriers, publications, publieesMois, appelsIa, echecsIa, images, echecsPub,
                essais, payants, revenu, expirent, expires);
    }

    // ------------------------------------------------------------ formules

    @Transactional(readOnly = true)
    public List<FormuleAdmin> formules() {
        Map<String, Integer> parFormule = new java.util.HashMap<>();
        jdbc.query("""
                SELECT formule, count(*) AS n FROM (
                    SELECT DISTINCT ON (workspace_id) workspace_id, formule FROM abonnement ORDER BY workspace_id, fin DESC
                ) t GROUP BY formule
                """, rs -> {
            parFormule.put(rs.getString("formule"), rs.getInt("n"));
        });
        return formules.toutes().stream()
                .map(l -> new FormuleAdmin(l.formule().name(), l.nom(), l.prixMensuelXaf(), l.maxClients(), l.maxMembres(),
                        l.calendriersParMois(), l.imagesParMois(), parFormule.getOrDefault(l.formule().name(), 0)))
                .toList();
    }

    // ------------------------------------------------------------ abonnement d'un compte

    /**
     * Attribue une formule à un compte jusqu'à une date (paiement reçu hors application, geste commercial, prolongation d'essai, suspension
     * en mettant une date passée). L'abonnement courant est modifié sur place : c'est lui que l'application lit. L'opération est journalisée.
     */
    @Transactional
    public Abonnement changerAbonnement(Session s, UUID workspaceId, Formule formule, LocalDate fin) {
        s.exigerSuperAdmin();
        Abonnement a = abonnements.findFirstByWorkspaceIdOrderByFinDesc(workspaceId).orElseThrow(() -> ApiException.introuvable("Compte"));
        if (formule == null || fin == null) throw ApiException.requeteInvalide("La formule et la date de fin sont obligatoires");
        if (fin.isBefore(LocalDate.of(2020, 1, 1)) || fin.isAfter(LocalDate.now().plusYears(100))) throw ApiException.requeteInvalide("Date de fin invalide");
        Formule avant = a.getFormule();
        LocalDate finAvant = a.getFin();
        if (avant != formule) a.setDebut(LocalDate.now());
        a.setFormule(formule);
        a.setFin(fin);
        if (formule == Formule.ESSAI) {
            a.setStatut(Abonnement.Statut.ESSAI);
            a.setMode(Abonnement.Mode.ESSAI);
        } else {
            a.setStatut(Abonnement.Statut.ACTIF);
            // Un paiement en ligne reste un paiement en ligne ; une attribution depuis un essai est un règlement hors application
            if (a.getMode() == null || a.getMode() == Abonnement.Mode.ESSAI) a.setMode(Abonnement.Mode.ESPECES);
        }
        abonnements.save(a);
        journal.enregistrer(workspaceId, s.userId(), null, Actions.ABONNEMENT_MODIFIE, formule.name(),
                avant + " → " + formule + " · fin " + finAvant + " → " + fin);
        return a;
    }

    // ------------------------------------------------------------ journal

    @Transactional(readOnly = true)
    public PageJournal journal(UUID espaceId, String action, int page, int taille) {
        int t = Math.max(1, Math.min(taille, 100));
        int p = Math.max(0, page);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (espaceId != null) {
            where.append(" AND a.workspace_id = ?");
            args.add(espaceId);
        }
        if (action != null && !action.isBlank()) {
            where.append(" AND a.action = ?");
            args.add(action.trim());
        }
        Long total = jdbc.queryForObject("SELECT count(*) FROM journal_activite a" + where, Long.class, args.toArray());
        List<Object> argsPage = new ArrayList<>(args);
        argsPage.add(t);
        argsPage.add(p * t);
        List<Ligne> lignes = jdbc.query("""
                SELECT a.created_at, a.action, a.cible, a.detail, a.workspace_id, w.nom AS espace,
                       COALESCE(u.nom || ' (' || u.email || ')', a.auteur, 'Système') AS auteur
                FROM journal_activite a
                LEFT JOIN workspace w ON w.id = a.workspace_id
                LEFT JOIN app_user u ON u.id = a.utilisateur_id
                """ + where + " ORDER BY a.created_at DESC LIMIT ? OFFSET ?",
                (rs, i) -> new Ligne(instant(rs.getTimestamp("created_at")), rs.getString("action"),
                        Actions.LIBELLES.getOrDefault(rs.getString("action"), rs.getString("action")), rs.getString("espace"),
                        rs.getObject("workspace_id", UUID.class), rs.getString("auteur"), rs.getString("cible"), rs.getString("detail")),
                argsPage.toArray());
        return new PageJournal(total == null ? 0 : total, p, t, lignes, Actions.LIBELLES);
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
