package com.okpost.analyse;

import com.okpost.calendrier.PublicationRepository;
import com.okpost.client.Client;
import com.okpost.reseaux.Chiffreur;
import com.okpost.reseaux.CompteSocial;
import com.okpost.reseaux.CompteSocialRepository;
import com.okpost.reseaux.ComptesSociauxService;
import com.okpost.reseaux.MetaApi;
import com.okpost.reseaux.MetaApi.PostPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lecture des publications de la Page Facebook d'un client : résumé envoyé à l'IA avant un calendrier,
 * et bilan d'une période comparé à la période précédente.
 */
@Service
public class AnalysePageService {

    private static final Logger log = LoggerFactory.getLogger(AnalysePageService.class);
    private static final Locale FR = Locale.FRENCH;
    private static final String[] CRENEAUX = {"nuit (22 h – 6 h)", "matin (6 h – 10 h)", "fin de matinée (10 h – 14 h)",
            "après-midi (14 h – 18 h)", "soirée (18 h – 22 h)"};

    public record Statistiques(int publications, int reactions, int commentaires, int partages, Integer vues,
                               int engagement, double engagementMoyen, Double vuesMoyennes,
                               Map<String, Integer> parType, Map<String, Double> engagementParType,
                               Map<String, Double> engagementParJour, Map<String, Double> engagementParCreneau) {}

    public record PostResume(Instant date, String extrait, String type, int reactions, int commentaires, int partages,
                             Integer vues, String lien) {}

    public record Bilan(String page, LocalDate debut, LocalDate fin, LocalDate debutPrecedent, LocalDate finPrecedent,
                        Statistiques actuelle, Statistiques precedente, Integer abonnes, Integer abonnesDebut,
                        LocalDate jourReleveDebut, long publieesParOkPost, List<PostResume> meilleurs,
                        List<PostResume> moinsBons, boolean vuesDisponibles, boolean commentairesDisponibles,
                        String remarque) {}

    /** Accès à la Page active du client. */
    record Acces(UUID compteId, String pageId, String nom, String jeton) {}

    private final CompteSocialRepository comptes;
    private final Chiffreur chiffreur;
    private final MetaApi meta;
    private final RelevePageRepository releves;
    private final PublicationRepository publications;
    private final TransactionTemplate tx;

    public AnalysePageService(CompteSocialRepository comptes, Chiffreur chiffreur, MetaApi meta,
                              RelevePageRepository releves, PublicationRepository publications, TransactionTemplate tx) {
        this.comptes = comptes;
        this.chiffreur = chiffreur;
        this.meta = meta;
        this.releves = releves;
        this.publications = publications;
        this.tx = tx;
    }

    public boolean pageReliee(UUID clientId) {
        return comptes.findFirstByClientIdAndReseauAndActifTrue(clientId, ComptesSociauxService.FACEBOOK).isPresent();
    }

    Optional<Acces> acces(UUID clientId) {
        return comptes.findFirstByClientIdAndReseauAndActifTrue(clientId, ComptesSociauxService.FACEBOOK)
                .map(c -> new Acces(c.getId(), c.getCompteExterneId(), c.getNom(), chiffreur.dechiffrer(c.getJetonChiffre())));
    }

    // ------------------------------------------------------------------ Bilan

    public Bilan bilan(Client client, LocalDate debut, LocalDate fin) {
        Acces a = acces(client.getId()).orElseThrow(() -> new MetaApi.MetaException(
                "Aucune Page Facebook reliée à " + client.getNom() + " : relie-la dans « Réseaux connectés »."));
        ZoneId zone = zone(client);
        long jours = ChronoUnit.DAYS.between(debut, fin) + 1;
        LocalDate finPrec = debut.minusDays(1);
        LocalDate debutPrec = finPrec.minusDays(jours - 1);

        MetaApi.PostsPage actuels = meta.posts(a.pageId(), a.jeton(), debut.atStartOfDay(zone).toInstant(),
                fin.plusDays(1).atStartOfDay(zone).toInstant());
        MetaApi.PostsPage precedents = meta.posts(a.pageId(), a.jeton(), debutPrec.atStartOfDay(zone).toInstant(),
                finPrec.plusDays(1).atStartOfDay(zone).toInstant());

        Integer abonnes = releverAbonnes(a);
        Optional<RelevePage> releveDebut = releves.findFirstByCompteIdAndJourLessThanEqualOrderByJourDesc(a.compteId(), debut);
        long okpost = publications.compterPublieesParOkPost(client.getId(), debut.atStartOfDay(zone).toInstant(),
                fin.plusDays(1).atStartOfDay(zone).toInstant());

        List<PostPage> tries = actuels.posts().stream().sorted(Comparator.comparingDouble(this::score).reversed()).toList();
        List<PostResume> meilleurs = tries.stream().limit(5).map(AnalysePageService::resume).toList();
        List<PostResume> moinsBons = tries.size() > 6
                ? tries.subList(tries.size() - 3, tries.size()).stream().map(AnalysePageService::resume).toList()
                : List.of();
        return new Bilan(a.nom(), debut, fin, debutPrec, finPrec, statistiques(actuels.posts(), zone),
                statistiques(precedents.posts(), zone), abonnes, releveDebut.map(RelevePage::getAbonnes).orElse(null),
                releveDebut.map(RelevePage::getJour).orElse(null), okpost, meilleurs, moinsBons,
                actuels.vuesDisponibles(), actuels.commentairesDisponibles(), actuels.remarque());
    }

    /** Texte du bilan pour l'IA (recommandations). */
    public String texteBilan(Bilan b) {
        StringBuilder t = new StringBuilder();
        t.append("Période : du ").append(b.debut()).append(" au ").append(b.fin()).append('\n');
        t.append("Période précédente : du ").append(b.debutPrecedent()).append(" au ").append(b.finPrecedent()).append('\n');
        ligneStats(t, "Période", b.actuelle());
        ligneStats(t, "Période précédente", b.precedente());
        if (b.abonnes() != null) t.append("Abonnés aujourd'hui : ").append(b.abonnes())
                .append(b.abonnesDebut() != null ? " (" + b.abonnesDebut() + " le " + b.jourReleveDebut() + ")" : "").append('\n');
        t.append("Meilleures publications :\n");
        b.meilleurs().forEach(p -> t.append("- ").append(lignePost(p)).append('\n'));
        if (!b.moinsBons().isEmpty()) {
            t.append("Publications les moins performantes :\n");
            b.moinsBons().forEach(p -> t.append("- ").append(lignePost(p)).append('\n'));
        }
        return t.toString();
    }

    // ------------------------------------------------------------------ Analyse avant calendrier

    /**
     * Résumé des 90 derniers jours de la Page, ajouté à la fiche client pour la génération du calendrier.
     * Retourne null si aucune Page n'est reliée ou si Facebook ne répond pas : la génération continue sans.
     */
    public String resumePourCalendrier(Client client) {
        Optional<Acces> a = acces(client.getId());
        if (a.isEmpty()) return null;
        ZoneId zone = zone(client);
        Instant fin = Instant.now();
        Instant debut = fin.minus(90, ChronoUnit.DAYS);
        List<PostPage> posts;
        try {
            posts = meta.posts(a.get().pageId(), a.get().jeton(), debut, fin).posts();
        } catch (RuntimeException e) {
            log.warn("Analyse de la Page de {} impossible : {}", client.getNom(), e.getMessage());
            return null;
        }
        if (posts.isEmpty()) {
            return "La Page « " + a.get().nom() + " » n'a rien publié ces 90 derniers jours : pas d'historique à exploiter.";
        }
        Statistiques s = statistiques(posts, zone);
        StringBuilder t = new StringBuilder();
        t.append("Page « ").append(a.get().nom()).append(" », 90 derniers jours : ").append(s.publications())
                .append(" publications (environ ").append(Math.round(s.publications() / 12.9 * 10) / 10.0).append(" par semaine).\n");
        t.append("Engagement moyen par publication (réactions + commentaires + partages) : ")
                .append(arrondi(s.engagementMoyen())).append(s.vuesMoyennes() != null ? " ; vues moyennes : " + Math.round(s.vuesMoyennes()) : "").append(".\n");
        t.append("Engagement moyen par format : ").append(liste(s.engagementParType(), s.parType())).append(".\n");
        t.append("Engagement moyen par jour : ").append(classement(s.engagementParJour())).append(".\n");
        t.append("Engagement moyen par créneau horaire : ").append(classement(s.engagementParCreneau())).append(".\n");
        t.append("Les 5 publications qui ont le mieux marché :\n");
        posts.stream().sorted(Comparator.comparingDouble(this::score).reversed()).limit(5)
                .forEach(p -> t.append("- ").append(lignePost(resume(p))).append('\n'));
        t.append("Sujets publiés récemment (ne pas les reprendre à l'identique) :\n");
        posts.stream().filter(p -> !p.message().isBlank()).limit(20)
                .forEach(p -> t.append("- ").append(extrait(p.message(), 110)).append('\n'));
        t.append("Utilise cet historique pour : reprendre les sujets et formats qui marchent sous un angle nouveau, "
                + "éviter les répétitions, garder le ton réel de la Page, et privilégier les jours et créneaux les plus porteurs.");
        return t.toString();
    }

    // ------------------------------------------------------------------ Relevés d'abonnés

    /** Chaque matin : relevé des abonnés de toutes les Pages actives, pour comparer les périodes. */
    @Scheduled(cron = "0 10 6 * * *", zone = "Africa/Douala")
    public void releverTout() {
        List<CompteSocial> actifs = tx.execute(s -> comptes.findAll().stream()
                .filter(c -> c.isActif() && ComptesSociauxService.FACEBOOK.equals(c.getReseau())).toList());
        if (actifs == null) return;
        for (CompteSocial c : actifs) {
            try {
                releverAbonnes(new Acces(c.getId(), c.getCompteExterneId(), c.getNom(), chiffreur.dechiffrer(c.getJetonChiffre())));
            } catch (RuntimeException e) {
                log.warn("Relevé des abonnés de {} impossible : {}", c.getNom(), e.getMessage());
            }
        }
    }

    private Integer releverAbonnes(Acces a) {
        Integer n = meta.abonnes(a.pageId(), a.jeton());
        if (n == null) return null;
        LocalDate aujourdhui = LocalDate.now(ZoneId.of("Africa/Douala"));
        tx.executeWithoutResult(s -> {
            RelevePage r = releves.findByCompteIdAndJour(a.compteId(), aujourdhui).orElseGet(RelevePage::new);
            r.setCompteId(a.compteId());
            r.setJour(aujourdhui);
            r.setAbonnes(n);
            releves.save(r);
        });
        return n;
    }

    // ------------------------------------------------------------------ Calculs

    static Statistiques statistiques(List<PostPage> posts, ZoneId zone) {
        int reactions = posts.stream().mapToInt(PostPage::reactions).sum();
        int commentaires = posts.stream().mapToInt(PostPage::commentaires).sum();
        int partages = posts.stream().mapToInt(PostPage::partages).sum();
        List<Integer> vues = posts.stream().map(PostPage::vues).filter(java.util.Objects::nonNull).toList();
        Integer totalVues = vues.isEmpty() ? null : vues.stream().mapToInt(Integer::intValue).sum();
        int engagement = reactions + commentaires + partages;
        Map<String, Integer> parType = posts.stream().collect(Collectors.groupingBy(PostPage::type, LinkedHashMap::new,
                Collectors.summingInt(p -> 1)));
        Map<String, Double> engType = moyennePar(posts, PostPage::type);
        List<PostPage> dates = posts.stream().filter(p -> p.date() != null).toList();
        Map<String, Double> engJour = moyennePar(dates, p -> p.date().atZone(zone).getDayOfWeek()
                .getDisplayName(TextStyle.FULL, FR));
        Map<String, Double> engCreneau = moyennePar(dates, p -> creneau(p.date().atZone(zone)));
        return new Statistiques(posts.size(), reactions, commentaires, partages, totalVues, engagement,
                posts.isEmpty() ? 0 : arrondi((double) engagement / posts.size()),
                vues.isEmpty() ? null : arrondi(vues.stream().mapToInt(Integer::intValue).average().orElse(0)),
                parType, engType, trierJours(engJour), engCreneau);
    }

    private static Map<String, Double> moyennePar(List<PostPage> posts, Function<PostPage, String> cle) {
        Map<String, Double> m = new LinkedHashMap<>();
        posts.stream().collect(Collectors.groupingBy(cle, LinkedHashMap::new, Collectors.averagingInt(PostPage::engagement)))
                .forEach((k, v) -> m.put(k, arrondi(v)));
        return m;
    }

    private static Map<String, Double> trierJours(Map<String, Double> m) {
        Map<String, Double> r = new LinkedHashMap<>();
        for (DayOfWeek d : DayOfWeek.values()) {
            String nom = d.getDisplayName(TextStyle.FULL, FR);
            if (m.containsKey(nom)) r.put(nom, m.get(nom));
        }
        return r;
    }

    private static String creneau(ZonedDateTime d) {
        int h = d.getHour();
        if (h < 6 || h >= 22) return CRENEAUX[0];
        if (h < 10) return CRENEAUX[1];
        if (h < 14) return CRENEAUX[2];
        if (h < 18) return CRENEAUX[3];
        return CRENEAUX[4];
    }

    /** Classement : engagement d'abord, vues ensuite quand elles existent. */
    private double score(PostPage p) {
        return p.engagement() * 10.0 + (p.vues() == null ? 0 : p.vues() / 100.0);
    }

    private static PostResume resume(PostPage p) {
        return new PostResume(p.date(), extrait(p.message(), 160), p.type(), p.reactions(), p.commentaires(),
                p.partages(), p.vues(), p.lien());
    }

    private static String lignePost(PostResume p) {
        return "[" + p.type() + (p.date() == null ? "" : ", " + p.date().toString().substring(0, 10)) + "] "
                + (p.extrait().isBlank() ? "(sans texte)" : p.extrait()) + " — " + p.reactions() + " réactions, "
                + p.commentaires() + " commentaires, " + p.partages() + " partages"
                + (p.vues() == null ? "" : ", " + p.vues() + " vues");
    }

    private static void ligneStats(StringBuilder t, String titre, Statistiques s) {
        t.append(titre).append(" : ").append(s.publications()).append(" publications, ").append(s.reactions())
                .append(" réactions, ").append(s.commentaires()).append(" commentaires, ").append(s.partages())
                .append(" partages").append(s.vues() == null ? "" : ", " + s.vues() + " vues")
                .append(", engagement moyen ").append(s.engagementMoyen()).append(" ; par format : ")
                .append(liste(s.engagementParType(), s.parType())).append('\n');
    }

    private static String liste(Map<String, Double> moyennes, Map<String, Integer> nombres) {
        if (moyennes.isEmpty()) return "—";
        return moyennes.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> e.getKey() + " " + e.getValue() + " (" + nombres.getOrDefault(e.getKey(), 0) + " posts)")
                .collect(Collectors.joining(", "));
    }

    private static String classement(Map<String, Double> m) {
        if (m.isEmpty()) return "—";
        return m.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(", "));
    }

    private static String extrait(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    private static double arrondi(double d) {
        return Math.round(d * 10) / 10.0;
    }

    private static ZoneId zone(Client c) {
        try {
            return ZoneId.of(c.getFuseau() == null ? "Africa/Douala" : c.getFuseau());
        } catch (RuntimeException e) {
            return ZoneId.of("Africa/Douala");
        }
    }

    static List<String> creneaux() { return new ArrayList<>(List.of(CRENEAUX)); }
}
