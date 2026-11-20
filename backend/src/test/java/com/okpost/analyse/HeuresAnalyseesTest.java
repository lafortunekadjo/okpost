package com.okpost.analyse;

import com.okpost.analyse.AnalysePageService.Heures;
import com.okpost.analyse.AnalysePageService.HeuresRecommandees;
import com.okpost.reseaux.MetaApi.PostPage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HeuresAnalyseesTest {

    private static final ZoneId DOUALA = ZoneId.of("Africa/Douala"); // UTC+1

    /** Publication à l'heure locale donnée (heure de Douala), avec cet engagement total. */
    private static PostPage post(int jour, int heureLocale, int engagement) {
        Instant date = Instant.parse(String.format("2026-10-%02dT%02d:00:00Z", jour, heureLocale - 1));
        return new PostPage("id" + jour + heureLocale, "texte", date, "https://exemple.test", "photo", engagement, 0, 0, null);
    }

    private static List<PostPage> historique() {
        List<PostPage> posts = new ArrayList<>();
        // 19 h : très porteuse (4 publications) ; 12 h : bonne (3) ; 7 h : très faible (3) ; 15 h : moyenne (2)
        for (int j = 1; j <= 4; j++) posts.add(post(j, 19, 60));
        for (int j = 5; j <= 7; j++) posts.add(post(j, 12, 45));
        for (int j = 8; j <= 10; j++) posts.add(post(j, 7, 3));
        for (int j = 11; j <= 12; j++) posts.add(post(j, 15, 25));
        return posts;
    }

    @Test
    void trouveLesMeilleuresEtLesPiresHeuresDansLeFuseauDuClient() {
        Heures h = AnalysePageService.heures(historique(), DOUALA);
        assertThat(h.fiable()).isTrue();
        assertThat(h.meilleures()).containsExactly(19, 12);
        assertThat(h.aEviter()).containsExactly(7);
        assertThat(h.texte()).startsWith("19 h → 60 (4 pub.)");
    }

    @Test
    void unHistoriqueTropCourtNeFixeAucuneHeure() {
        Heures h = AnalysePageService.heures(historique().subList(0, 6), DOUALA);
        assertThat(h.fiable()).isFalse();
        assertThat(h.meilleures()).isEmpty();
        assertThat(h.aEviter()).isEmpty();
    }

    @Test
    void uneHeureAvecUneSeulePublicationNEstPasRetenue() {
        List<PostPage> posts = historique();
        posts.add(post(20, 21, 500)); // un seul post à 21 h, énorme mais isolé
        assertThat(AnalysePageService.heures(posts, DOUALA).meilleures()).doesNotContain(21);
    }

    @Test
    void relitLesHeuresDansLeTexteDAnalyse() {
        String analyse = "Page « X ».\nMeilleures heures de publication : 12 h, 19 h.\nHeures peu porteuses : 7 h, 22 h.\nSuite.";
        HeuresRecommandees reco = AnalysePageService.heuresDepuisAnalyse(analyse);
        assertThat(reco.meilleures()).containsExactly(12, 19);
        assertThat(reco.aEviter()).containsExactly(7, 22);
        assertThat(reco.plusProche(8)).isEqualTo(12);
        assertThat(reco.plusProche(21)).isEqualTo(19);
    }

    @Test
    void sansAnalyseOuSansHeuresFiablesRienNEstImpose() {
        assertThat(AnalysePageService.heuresDepuisAnalyse(null).meilleures()).isEmpty();
        assertThat(AnalysePageService.heuresDepuisAnalyse("Historique trop court pour fixer des heures de publication fiables.").meilleures()).isEmpty();
    }
}
