package com.okpost;

import com.okpost.ia.GenerateurIA;
import com.okpost.ia.Modeles;
import com.okpost.ia.Modeles.BriefVisuel;
import com.okpost.ia.Modeles.DemandeCalendrier;
import com.okpost.ia.Modeles.DetailGenere;
import com.okpost.ia.Modeles.GrilleGeneree;
import com.okpost.ia.Modeles.IdeeADetailler;
import com.okpost.ia.Modeles.IdeeGeneree;
import com.okpost.ia.Modeles.PlanVideo;
import com.okpost.ia.Modeles.ScenarioVideo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Fournisseur IA de test : réponses déterministes, sans appel réseau. */
public class IaFactice implements GenerateurIA {

    @Override public String nom() { return "factice"; }
    @Override public boolean configure() { return true; }

    @Override
    public GrilleGeneree genererCalendrier(UUID ws, String fiche, DemandeCalendrier d) {
        List<IdeeGeneree> idees = new ArrayList<>();
        int i = 0;
        for (var date = d.debut(); !date.isAfter(d.fin()); date = date.plusDays(2)) {
            String reseau = d.reseaux().get(i % d.reseaux().size());
            String format = "TIKTOK".equals(reseau) ? "VIDEO" : (i % 3 == 0 ? "CARROUSEL" : "IMAGE");
            idees.add(new IdeeGeneree(date, reseau, format, "Éducation", "Idée " + i, "NOTORIETE", null, "18:30", "Heure de test"));
            i++;
        }
        return new GrilleGeneree("Stratégie de test", List.of("Éducation", "Offre"), idees);
    }

    @Override
    public List<DetailGenere> genererDetails(UUID ws, String fiche, String langue, List<IdeeADetailler> idees) {
        return idees.stream().map(x -> "VIDEO".equals(x.format())
                ? new DetailGenere(x.index(), "Légende vidéo " + x.index(), List.of("#Douala"), "Écrivez-nous",
                    null, new ScenarioVideo("Titre", 30, "Accroche", List.of(new PlanVideo(1, 3, "Plan", "Texte", "Voix", "Son")), List.of("Téléphone")))
                : new DetailGenere(x.index(), "Légende " + x.index(), List.of("#Douala"), "Écrivez-nous",
                    new BriefVisuel(x.format(), "Description", "A photo of...", "4:5",
                            "CARROUSEL".equals(x.format()) ? List.of(new Modeles.TexteCarrousel(1, "Titre", "Texte")) : null,
                            "Accroche de test", null, null, null, null, null), null))
                .toList();
    }

    @Override
    public String regenererChamp(UUID ws, String fiche, String langue, String publication, String champ, String consigne) {
        return "Texte régénéré : " + consigne;
    }
}
