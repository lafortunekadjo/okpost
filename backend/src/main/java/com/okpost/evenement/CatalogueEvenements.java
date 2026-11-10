package com.okpost.evenement;

import com.okpost.client.Client;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Calcule les dates du catalogue et choisit les événements pertinents pour un client sur une période :
 * pays du client (Cameroun si non renseigné), secteur (mots-clés), fêtes religieuses selon la fiche.
 */
@Service
public class CatalogueEvenements {

    public record Proposition(UUID id, String nom, LocalDate debut, LocalDate fin, String categorie,
                              String raison, String angle, boolean approximative) {}

    private final EvenementRepository repo;

    public CatalogueEvenements(EvenementRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<Proposition> proposer(Client client, LocalDate debut, LocalDate fin) {
        String pays = normaliser(client.getPays() == null || client.getPays().isBlank() ? "cameroun" : client.getPays());
        String secteur = normaliser((client.getSecteur() == null ? "" : client.getSecteur()) + " "
                + (client.getNom() == null ? "" : client.getNom()));
        List<Proposition> resultat = new ArrayList<>();
        for (Evenement e : repo.findByActifTrue()) {
            if (e.getCategorie() == Evenement.Categorie.RELIGIEUX && !client.isFetesReligieuses()) continue;
            if (!e.getPays().isBlank() && motTrouve(e.getPays(), pays, true) == null) continue;
            String motSecteur = motTrouve(e.getSecteurs(), secteur, false);
            if (!e.getSecteurs().isBlank() && motSecteur == null) continue;
            for (LocalDate d : occurrences(e, debut, fin)) {
                LocalDate dFin = d.plusDays(Math.max(1, e.getDureeJours()) - 1L);
                resultat.add(new Proposition(e.getId(), e.getNom(), d, e.getDureeJours() > 1 ? dFin : null,
                        e.getCategorie().name(), raison(e, client), e.getAngle(), e.isApproximative()));
            }
        }
        resultat.sort(Comparator.comparing(Proposition::debut).thenComparing(Proposition::nom));
        return resultat;
    }

    /** Débuts des occurrences de l'événement qui touchent la période [debut, fin]. */
    static List<LocalDate> occurrences(Evenement e, LocalDate debut, LocalDate fin) {
        List<LocalDate> dates = new ArrayList<>();
        long duree = Math.max(1, e.getDureeJours());
        for (int annee = debut.getYear() - 1; annee <= fin.getYear(); annee++) {
            LocalDate d = date(e, annee);
            if (d == null) continue;
            LocalDate dFin = d.plusDays(duree - 1);
            if (!dFin.isBefore(debut) && !d.isAfter(fin) && !dates.contains(d)) dates.add(d);
        }
        return dates;
    }

    static LocalDate date(Evenement e, int annee) {
        try {
            return switch (e.getRegle()) {
                case FIXE -> LocalDate.of(annee, e.getMois(), e.getJour()).plusDays(e.getDecalage());
                case PAQUES -> paques(annee).plusDays(e.getDecalage());
                case DATE -> e.getDateUnique() != null && e.getDateUnique().getYear() == annee ? e.getDateUnique() : null;
                case NIEME -> {
                    DayOfWeek j = DayOfWeek.of(e.getJourSemaine());
                    LocalDate premier = LocalDate.of(annee, e.getMois(), 1);
                    LocalDate d = e.getNieme() < 0
                            ? premier.with(TemporalAdjusters.lastInMonth(j))
                            : premier.with(TemporalAdjusters.dayOfWeekInMonth(e.getNieme(), j));
                    yield d.plusDays(e.getDecalage());
                }
            };
        } catch (RuntimeException ex) {
            return null; // règle incomplète saisie à la main : ignorée plutôt que de casser la liste
        }
    }

    /** Dimanche de Pâques (calendrier grégorien, algorithme de Meeus). */
    static LocalDate paques(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mois = (h + l - 7 * m + 114) / 31, jour = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(y, mois, jour);
    }

    private static String raison(Evenement e, Client c) {
        return switch (e.getCategorie()) {
            case NATIONAL -> "Fête nationale" + (e.getPays().isBlank() ? "" : " (" + c.getPays() + ")");
            case RELIGIEUX -> "Fête religieuse" + (e.isApproximative() ? ", date à un jour près" : "");
            case COMMERCIAL -> "Temps fort commercial grand public";
            case SAISON -> "Période de l'année" + (e.isApproximative() ? ", dates indicatives" : "");
            case MONDIAL -> e.getSecteurs().isBlank() ? "Journée mondiale grand public"
                    : "Journée liée au secteur « " + (c.getSecteur() == null ? "" : c.getSecteur()) + " »";
        };
    }

    /**
     * Premier mot-clé présent dans le texte. Secteur : début de mot (« medic » trouve « médical », mais « sport »
     * ne trouve pas « transport »). Pays : mot entier (« niger » ne trouve pas « nigeria »).
     */
    static String motTrouve(String motsCles, String texte, boolean motEntier) {
        if (motsCles == null || motsCles.isBlank()) return null;
        return Arrays.stream(motsCles.split(",")).map(CatalogueEvenements::normaliser)
                .filter(m -> !m.isBlank() && java.util.regex.Pattern.compile(
                        "(^|[^a-z])" + java.util.regex.Pattern.quote(m) + (motEntier ? "($|[^a-z])" : "")).matcher(texte).find())
                .findFirst().orElse(null);
    }

    static String normaliser(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replace('’', '\'').trim();
    }
}
