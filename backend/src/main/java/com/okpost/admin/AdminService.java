package com.okpost.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Vue d'ensemble de la plateforme pour le super-admin : comptes, usage, consommation d'IA et journal d'activité. */
@Service
public class AdminService {

    public record Compte(UUID id, String nom, Instant creeLe, String emailAdmin, int membres, int clients, int calendriers,
                         int publications, int publiees, int imagesMois, int appelsIaMois, String formule, String statutAbonnement,
                         LocalDate finAbonnement, Instant derniereConnexion) {}

    public record Totaux(int comptes, int clients, int calendriers, int publications, int publieesMois, int appelsIaMois,
                         int echecsIaMois, int imagesMois, int echecsPublication7j) {}

    public record VueComptes(Totaux totaux, List<Compte> comptes) {}

    public record Ligne(Instant date, String action, String libelle, String espace, UUID espaceId, String auteur, String cible, String detail) {}

    public record PageJournal(long total, int page, int taille, List<Ligne> lignes, Map<String, String> actions) {}

    private final JdbcTemplate jdbc;

    public AdminService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public VueComptes comptes() {
        List<Compte> comptes = jdbc.query("""
                SELECT w.id, w.nom, w.created_at,
                  (SELECT u.email FROM app_user u WHERE u.workspace_id = w.id ORDER BY u.created_at LIMIT 1) AS email_admin,
                  (SELECT count(*) FROM app_user u WHERE u.workspace_id = w.id AND u.actif) AS membres,
                  (SELECT count(*) FROM client c WHERE c.workspace_id = w.id AND NOT c.archive) AS clients,
                  (SELECT count(*) FROM calendrier k WHERE k.workspace_id = w.id) AS calendriers,
                  (SELECT count(*) FROM publication p WHERE p.workspace_id = w.id) AS publications,
                  (SELECT count(*) FROM publication p WHERE p.workspace_id = w.id AND p.statut = 'PUBLIEE') AS publiees,
                  (SELECT count(*) FROM visuel_genere v WHERE v.workspace_id = w.id AND v.source = 'GENERE'
                      AND v.created_at >= date_trunc('month', now())) AS images_mois,
                  (SELECT count(*) FROM journal_ia j WHERE j.workspace_id = w.id
                      AND j.created_at >= date_trunc('month', now())) AS appels_ia_mois,
                  (SELECT a.formule FROM abonnement a WHERE a.workspace_id = w.id ORDER BY a.fin DESC LIMIT 1) AS formule,
                  (SELECT a.statut FROM abonnement a WHERE a.workspace_id = w.id ORDER BY a.fin DESC LIMIT 1) AS statut_abo,
                  (SELECT a.fin FROM abonnement a WHERE a.workspace_id = w.id ORDER BY a.fin DESC LIMIT 1) AS fin_abo,
                  (SELECT max(j.created_at) FROM journal_activite j WHERE j.workspace_id = w.id AND j.action = 'CONNEXION') AS derniere_connexion
                FROM workspace w
                ORDER BY w.created_at DESC
                """, (rs, i) -> new Compte(
                rs.getObject("id", UUID.class), rs.getString("nom"), instant(rs.getTimestamp("created_at")), rs.getString("email_admin"),
                rs.getInt("membres"), rs.getInt("clients"), rs.getInt("calendriers"), rs.getInt("publications"), rs.getInt("publiees"),
                rs.getInt("images_mois"), rs.getInt("appels_ia_mois"), rs.getString("formule"), rs.getString("statut_abo"),
                rs.getObject("fin_abo", LocalDate.class), instant(rs.getTimestamp("derniere_connexion"))));

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
                """, (rs, i) -> new Totaux(rs.getInt("comptes"), rs.getInt("clients"), rs.getInt("calendriers"), rs.getInt("publications"),
                rs.getInt("publiees_mois"), rs.getInt("appels_ia"), rs.getInt("echecs_ia"), rs.getInt("images"), rs.getInt("echecs_pub")));
        return new VueComptes(totaux, comptes);
    }

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
