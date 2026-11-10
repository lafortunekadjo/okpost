package com.okpost.ia;

import com.okpost.securite.Session;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ReglagesController {

    public record LigneConsommation(String fournisseur, String modele, long appels, long echecs,
                                    long jetonsEntree, long jetonsSortie) {}

    public record EtatIa(String fournisseurParDefaut, String fournisseurEspace, Map<String, Boolean> configures,
                         Map<String, Boolean> images, String imageParDefaut) {}

    public record ChoixIa(@NotBlank String fournisseur) {}

    private final JournalIaRepository journal;
    private final SelecteurIA selecteur;
    private final List<GenerateurImage> images;
    private final com.okpost.config.OkpostProperties props;

    public ReglagesController(JournalIaRepository journal, SelecteurIA selecteur, List<GenerateurImage> images,
                              com.okpost.config.OkpostProperties props) {
        this.images = images;
        this.props = props;
        this.journal = journal;
        this.selecteur = selecteur;
    }

    @GetMapping("/reglages/consommation")
    public List<LigneConsommation> consommation() {
        Session s = Session.courante();
        s.exigerAdmin();
        return journal.consommation(s.workspaceId(),
                        LocalDate.now().withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC)).stream()
                .map(c -> new LigneConsommation(c.getFournisseur(), c.getModele(), c.getAppels(), c.getEchecs(),
                        c.getJetonsEntree() == null ? 0 : c.getJetonsEntree(),
                        c.getJetonsSortie() == null ? 0 : c.getJetonsSortie()))
                .toList();
    }

    @GetMapping("/reglages/ia")
    public EtatIa etat() {
        Session s = Session.courante();
        Map<String, Boolean> moteurs = new java.util.LinkedHashMap<>();
        images.forEach(g -> moteurs.put(g.nom(), g.configure()));
        String voulu = props.ia().image() == null ? null : props.ia().image().fournisseur();
        String parDefaut = voulu != null && Boolean.TRUE.equals(moteurs.get(voulu.trim())) ? voulu.trim()
                : moteurs.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).findFirst().orElse(null);
        return new EtatIa(selecteur.parDefaut(), selecteur.pour(s.workspaceId()).nom(), selecteur.etat(), moteurs, parDefaut);
    }

    /** Super-admin : change le fournisseur par défaut de toute la plateforme, sans redéploiement. */
    @PutMapping("/admin/parametres/ia")
    public EtatIa changer(@Valid @RequestBody ChoixIa choix) {
        Session s = Session.courante();
        s.exigerSuperAdmin();
        selecteur.definirParDefaut(choix.fournisseur());
        return etat();
    }
}
