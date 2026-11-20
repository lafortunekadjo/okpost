package com.okpost.ia;

import com.okpost.ia.Modeles.DemandeCalendrier;
import com.okpost.ia.Modeles.GrilleGeneree;
import com.okpost.ia.Modeles.IdeeGeneree;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationReponsesTest {

    private final DemandeCalendrier demande = new DemandeCalendrier(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7),
            List.of("FACEBOOK"), Map.of("FACEBOOK", 1), null, null, "fr", List.of(), List.of());

    @Test
    void retireLesBalisesMarkdown() {
        assertThat(GenerateurSpringAi.nettoyer("```json\n{\"a\":1}\n```")).isEqualTo("{\"a\":1}");
        assertThat(GenerateurSpringAi.nettoyer("{\"a\":1}")).isEqualTo("{\"a\":1}");
    }

    @Test
    void refuseUneDateHorsPeriodeEtUnReseauNonDemande() {
        GrilleGeneree g = new GrilleGeneree("s", List.of("p"), List.of(
                new IdeeGeneree(LocalDate.of(2026, 12, 2), "FACEBOOK", "IMAGE", "p", "idée", "VENTE", null, null),
                new IdeeGeneree(LocalDate.of(2026, 11, 3), "LINKEDIN", "IMAGE", "p", "idée", "VENTE", null, null)));
        List<String> erreurs = GenerateurSpringAi.validerGrille(g, demande);
        assertThat(erreurs).hasSize(2);
    }

    @Test
    void accepteUneGrilleValide() {
        GrilleGeneree g = new GrilleGeneree("s", List.of("p"), List.of(
                new IdeeGeneree(LocalDate.of(2026, 11, 3), "facebook", "carrousel", "p", "idée", "VENTE", null, null)));
        assertThat(GenerateurSpringAi.validerGrille(g, demande)).isEmpty();
    }

    @Test
    void refuseUneGrilleIncomplete() {
        DemandeCalendrier deuxSemaines = new DemandeCalendrier(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 14),
                List.of("FACEBOOK"), Map.of("FACEBOOK", 3), null, null, "fr", List.of(), List.of());
        assertThat(deuxSemaines.attendus()).containsEntry("FACEBOOK", 6);
        GrilleGeneree uneSemaine = new GrilleGeneree("s", List.of("p"), List.of(
                new IdeeGeneree(LocalDate.of(2026, 11, 2), "FACEBOOK", "IMAGE", "p", "a", "VENTE", null, null),
                new IdeeGeneree(LocalDate.of(2026, 11, 4), "FACEBOOK", "IMAGE", "p", "b", "VENTE", null, null),
                new IdeeGeneree(LocalDate.of(2026, 11, 6), "FACEBOOK", "IMAGE", "p", "c", "VENTE", null, null)));
        assertThat(GenerateurSpringAi.validerGrille(uneSemaine, deuxSemaines)).hasSize(1);
    }

    @Test
    void nettoieLeNomDuModele() {
        assertThat(IaConfig.propre("\"gemini-3.8-flash\" ", "x")).isEqualTo("gemini-3.8-flash");
        assertThat(IaConfig.propre("models/gemini-3.8-flash", "x")).isEqualTo("gemini-3.8-flash");
        assertThat(IaConfig.propre("  ", "defaut")).isEqualTo("defaut");
        assertThat(IaConfig.propre(null, "defaut")).isEqualTo("defaut");
    }
}
