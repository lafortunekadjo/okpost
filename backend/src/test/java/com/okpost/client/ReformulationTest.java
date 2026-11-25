package com.okpost.client;

import com.okpost.ia.Prompts;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReformulationTest {

    @Test
    void lesGuillemetsEtRepresAjoutesParLIaSontRetires() {
        assertThat(ReformulationService.nettoyerSortie("  « Dentelle douce et sans armatures. »  ", 500)).isEqualTo("Dentelle douce et sans armatures.");
        assertThat(ReformulationService.nettoyerSortie("\"Coton doux\"", 500)).isEqualTo("Coton doux");
        assertThat(ReformulationService.nettoyerSortie("<<< Texte >>>", 500)).isEqualTo("Texte");
        assertThat(ReformulationService.nettoyerSortie(null, 500)).isEmpty();
    }

    @Test
    void leTexteEstRamenéALaTailleDuChampSansCouperUnMot() {
        String long_ = "mot ".repeat(200).trim();
        String coupe = ReformulationService.nettoyerSortie(long_, 100);
        assertThat(coupe.length()).isLessThanOrEqualTo(100);
        assertThat(coupe).endsWith("mot");
    }

    @Test
    void lesTroisModesExistentEtLesPromptsSontCompletementRemplis() {
        assertThat(ReformulationService.MODES).containsOnlyKeys("CORRIGER", "REFORMULER", "RACCOURCIR");
        Prompts prompts = new Prompts();
        String user = prompts.rendre("reformuler", Map.of("texte", "Soutien gorge en dentele", "sujet", "Émeraude",
                "instruction", ReformulationService.MODES.get("CORRIGER"), "langue", "français", "max", "500"));
        String systeme = prompts.rendre("reformuler-systeme", Map.of("fiche", "Nom : Boutique Aurore"));
        assertThat(user).contains("Soutien gorge en dentele").contains("500 caractères").contains("orthographe").doesNotContain("{{");
        assertThat(systeme).contains("Boutique Aurore").contains("N'invente rien").doesNotContain("{{");
    }
}
