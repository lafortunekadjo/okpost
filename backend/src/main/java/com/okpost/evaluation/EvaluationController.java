package com.okpost.evaluation;

import com.okpost.calendrier.Calendrier;
import com.okpost.calendrier.CalendrierRepository;
import com.okpost.calendrier.CalendrierService;
import com.okpost.calendrier.Publication;
import com.okpost.calendrier.PublicationRepository;
import com.okpost.securite.Session;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Grille de notation : 5 critères de 1 à 5 par publication, plus l'équilibre et un avis global par calendrier.
 * Sert à comparer la qualité des modèles d'IA sur les mêmes fiches clients.
 */
@RestController
@RequestMapping("/api/v1")
public class EvaluationController {

    public record Notes(@Min(1) @Max(5) Integer specificite, @Min(1) @Max(5) Integer exactitude,
                        @Min(1) @Max(5) Integer contexte, @Min(1) @Max(5) Integer faisabilite,
                        @Min(1) @Max(5) Integer pret, @Size(max = 2000) String commentaire) {}

    public record NoteVue(UUID publicationId, Integer specificite, Integer exactitude, Integer contexte,
                          Integer faisabilite, Integer pret, String commentaire) {}

    public record AvisCalendrier(@Min(1) @Max(5) Integer noteEquilibre, @Size(max = 4000) String avisGlobal) {}

    /** Moyennes par critère (null si aucune note), part des publications « envoyables » (prêt ≥ 4). */
    public record Moyennes(Double specificite, Double exactitude, Double contexte, Double faisabilite, Double pret,
                           Double globale, Integer partEnvoyables, int evaluees) {}

    public record EvaluationCalendrier(UUID calendrierId, String modele, int total, Integer noteEquilibre,
                                       String avisGlobal, Moyennes moyennes, List<NoteVue> notes) {}

    public record LigneSynthese(String modele, int calendriers, Moyennes moyennes, Double equilibre) {}

    private final EvaluationRepository evaluations;
    private final CalendrierService calendriers;
    private final CalendrierRepository calendrierRepo;
    private final PublicationRepository publications;

    public EvaluationController(EvaluationRepository evaluations, CalendrierService calendriers,
                                CalendrierRepository calendrierRepo, PublicationRepository publications) {
        this.evaluations = evaluations;
        this.calendriers = calendriers;
        this.calendrierRepo = calendrierRepo;
        this.publications = publications;
    }

    @GetMapping("/calendriers/{id}/evaluation")
    @Transactional(readOnly = true)
    public EvaluationCalendrier lire(@PathVariable UUID id) {
        Calendrier c = calendriers.charger(Session.courante(), id);
        List<EvaluationPublication> notes = evaluations.findByCalendrierId(id);
        return new EvaluationCalendrier(id, modele(c), (int) publications.countByCalendrierId(id), c.getNoteEquilibre(),
                c.getAvisGlobal(), moyennes(notes), notes.stream().map(EvaluationController::vue).toList());
    }

    @PutMapping("/publications/{id}/evaluation")
    @Transactional
    public NoteVue noter(@PathVariable UUID id, @Valid @RequestBody Notes n) {
        Session s = Session.courante();
        Publication p = calendriers.chargerPublication(s, id);
        EvaluationPublication e = evaluations.findById(id).orElseGet(() -> {
            EvaluationPublication x = new EvaluationPublication();
            x.setPublicationId(id);
            x.setCalendrierId(p.getCalendrierId());
            return x;
        });
        e.setSpecificite(n.specificite());
        e.setExactitude(n.exactitude());
        e.setContexte(n.contexte());
        e.setFaisabilite(n.faisabilite());
        e.setPret(n.pret());
        e.setCommentaire(n.commentaire() == null || n.commentaire().isBlank() ? null : n.commentaire().trim());
        e.setEvaluateurId(s.userId());
        return vue(evaluations.save(e));
    }

    @PutMapping("/calendriers/{id}/evaluation")
    @Transactional
    public EvaluationCalendrier avis(@PathVariable UUID id, @Valid @RequestBody AvisCalendrier a) {
        Calendrier c = calendriers.charger(Session.courante(), id);
        c.setNoteEquilibre(a.noteEquilibre());
        c.setAvisGlobal(a.avisGlobal() == null || a.avisGlobal().isBlank() ? null : a.avisGlobal().trim());
        calendrierRepo.save(c);
        return lire(id);
    }

    /** Comparatif des modèles sur tous les calendriers notés de l'espace. */
    @GetMapping("/evaluations/synthese")
    @Transactional(readOnly = true)
    public List<LigneSynthese> synthese() {
        Session s = Session.courante();
        s.exigerProprietaire();
        List<Calendrier> notes = calendrierRepo.findByWorkspaceId(s.workspaceId());
        Map<UUID, List<EvaluationPublication>> parCalendrier = evaluations
                .findByCalendrierIdIn(notes.stream().map(Calendrier::getId).toList()).stream()
                .collect(Collectors.groupingBy(EvaluationPublication::getCalendrierId));
        Map<String, List<Calendrier>> parModele = notes.stream()
                .filter(c -> parCalendrier.containsKey(c.getId()) || c.getNoteEquilibre() != null)
                .collect(Collectors.groupingBy(EvaluationController::modele, LinkedHashMap::new, Collectors.toList()));
        List<LigneSynthese> lignes = new ArrayList<>();
        parModele.forEach((modele, cals) -> {
            List<EvaluationPublication> toutes = cals.stream()
                    .flatMap(c -> parCalendrier.getOrDefault(c.getId(), List.of()).stream()).toList();
            Double equilibre = moyenne(cals.stream().map(Calendrier::getNoteEquilibre).toList());
            lignes.add(new LigneSynthese(modele, cals.size(), moyennes(toutes), equilibre));
        });
        lignes.sort((a, b) -> Double.compare(nz(b.moyennes().globale()), nz(a.moyennes().globale())));
        return lignes;
    }

    // ------------------------------------------------------------------

    private static String modele(Calendrier c) {
        return c.getFournisseurIa() == null || c.getFournisseurIa().isBlank() ? "inconnu" : c.getFournisseurIa();
    }

    static Moyennes moyennes(List<EvaluationPublication> notes) {
        Double spe = moy(notes, EvaluationPublication::getSpecificite);
        Double exa = moy(notes, EvaluationPublication::getExactitude);
        Double ctx = moy(notes, EvaluationPublication::getContexte);
        Double fai = moy(notes, EvaluationPublication::getFaisabilite);
        Double pre = moy(notes, EvaluationPublication::getPret);
        Double globale = java.util.stream.Stream.of(spe, exa, ctx, fai, pre).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).average().stream().map(d -> Math.round(d * 10) / 10.0).boxed()
                .findFirst().orElse(null);
        List<Integer> prets = notes.stream().map(EvaluationPublication::getPret).filter(Objects::nonNull).toList();
        Integer part = prets.isEmpty() ? null : (int) Math.round(100.0 * prets.stream().filter(x -> x >= 4).count() / prets.size());
        int evaluees = (int) notes.stream().filter(n -> n.getPret() != null || n.getSpecificite() != null
                || n.getExactitude() != null || n.getContexte() != null || n.getFaisabilite() != null).count();
        return new Moyennes(spe, exa, ctx, fai, pre, globale, part, evaluees);
    }

    private static Double moy(List<EvaluationPublication> notes, Function<EvaluationPublication, Integer> champ) {
        return moyenne(notes.stream().map(champ).toList());
    }

    private static Double moyenne(List<Integer> valeurs) {
        List<Integer> v = valeurs.stream().filter(Objects::nonNull).toList();
        if (v.isEmpty()) return null;
        return Math.round(10.0 * v.stream().mapToInt(Integer::intValue).average().orElse(0)) / 10.0;
    }

    private static double nz(Double d) { return d == null ? 0 : d; }

    private static NoteVue vue(EvaluationPublication e) {
        return new NoteVue(e.getPublicationId(), e.getSpecificite(), e.getExactitude(), e.getContexte(),
                e.getFaisabilite(), e.getPret(), e.getCommentaire());
    }
}
