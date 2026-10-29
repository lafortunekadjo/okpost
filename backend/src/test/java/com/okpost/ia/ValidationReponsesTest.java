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

    private final DemandeCalendrier demande = new DemandeCalendrier(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
            List.of("FACEBOOK"), Map.of("FACEBOOK", 3), null, null, "fr");

    @Test
    void retireLesBalisesMarkdown() {
        assertThat(GenerateurSpringAi.nettoyer("```json\n{\"a\":1}\n```")).isEqualTo("{\"a\":1}");
        assertThat(GenerateurSpringAi.nettoyer("{\"a\":1}")).isEqualTo("{\"a\":1}");
    }

    @Test
    void refuseUneDateHorsPeriodeEtUnReseauNonDemande() {
        GrilleGeneree g = new GrilleGeneree("s", List.of("p"), List.of(
                new IdeeGeneree(LocalDate.of(2026, 12, 2), "FACEBOOK", "IMAGE", "p", "idée", "VENTE", null),
                new IdeeGeneree(LocalDate.of(2026, 11, 3), "LINKEDIN", "IMAGE", "p", "idée", "VENTE", null)));
        List<String> erreurs = GenerateurSpringAi.validerGrille(g, demande);
        assertThat(erreurs).hasSize(2);
    }

    @Test
    void accepteUneGrilleValide() {
        GrilleGeneree g = new GrilleGeneree("s", List.of("p"), List.of(
                new IdeeGeneree(LocalDate.of(2026, 11, 3), "facebook", "carrousel", "p", "idée", "VENTE", null)));
        assertThat(GenerateurSpringAi.validerGrille(g, demande)).isEmpty();
    }
}
