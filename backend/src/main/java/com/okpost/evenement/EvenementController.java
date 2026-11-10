package com.okpost.evenement;

import com.okpost.calendrier.FicheTexte;
import com.okpost.client.Client;
import com.okpost.client.ClientService;
import com.okpost.commun.ApiException;
import com.okpost.ia.Modeles.TempsFortSuggere;
import com.okpost.ia.SelecteurIA;
import com.okpost.securite.Session;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class EvenementController {

    public record DemandeSuggestions(@NotNull LocalDate debut, @NotNull LocalDate fin, List<String> deja) {}

    public record EvenementVue(UUID id, String nom, String categorie, String regle, Integer mois, Integer jour,
                               Integer nieme, Integer jourSemaine, int decalage, LocalDate dateUnique, int dureeJours,
                               String secteurs, String pays, String angle, boolean approximative, boolean actif) {}

    public record EvenementForm(@NotBlank @Size(max = 200) String nom, @NotNull Evenement.Categorie categorie,
                                LocalDate dateUnique, @Min(1) @Max(12) Integer mois, @Min(1) @Max(31) Integer jour,
                                @Min(1) @Max(92) Integer dureeJours, @Size(max = 1000) String secteurs,
                                @Size(max = 500) String pays, @Size(max = 500) String angle, Boolean approximative) {}

    private final CatalogueEvenements catalogue;
    private final EvenementRepository repo;
    private final ClientService clients;
    private final SelecteurIA selecteur;

    public EvenementController(CatalogueEvenements catalogue, EvenementRepository repo, ClientService clients,
                               SelecteurIA selecteur) {
        this.catalogue = catalogue;
        this.repo = repo;
        this.clients = clients;
        this.selecteur = selecteur;
    }

    /** Fêtes et journées du catalogue qui conviennent au client sur la période. */
    @GetMapping("/clients/{clientId}/evenements")
    public List<CatalogueEvenements.Proposition> proposer(@PathVariable UUID clientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        verifierPeriode(debut, fin);
        Client c = clients.charger(Session.courante(), clientId);
        return catalogue.proposer(c, debut, fin);
    }

    /** Temps forts propres au métier du client, proposés par l'IA (à vérifier). */
    @PostMapping("/clients/{clientId}/evenements/suggestions")
    public List<TempsFortSuggere> suggerer(@PathVariable UUID clientId, @Valid @RequestBody DemandeSuggestions d) {
        verifierPeriode(d.debut(), d.fin());
        Session s = Session.courante();
        Client c = clients.charger(s, clientId);
        return selecteur.pour(s.workspaceId()).suggererTempsForts(s.workspaceId(), FicheTexte.de(c), d.debut(), d.fin(),
                d.deja() == null ? List.of() : d.deja().stream().limit(60).toList());
    }

    // ---------- Super-admin : gestion du catalogue ----------

    @GetMapping("/admin/evenements")
    public List<EvenementVue> lister() {
        Session.courante().exigerSuperAdmin();
        return repo.findAllByOrderByCategorieAscMoisAscJourAsc().stream().map(EvenementController::vue).toList();
    }

    /** Ajout simple : date fixe chaque année (mois + jour) ou date unique (fête mobile, événement daté). */
    @PostMapping("/admin/evenements")
    @ResponseStatus(HttpStatus.CREATED)
    public EvenementVue ajouter(@Valid @RequestBody EvenementForm f) {
        Session.courante().exigerSuperAdmin();
        Evenement e = new Evenement();
        e.setNom(f.nom().trim());
        e.setCategorie(f.categorie());
        if (f.dateUnique() != null) {
            e.setRegle(Evenement.Regle.DATE);
            e.setDateUnique(f.dateUnique());
        } else if (f.mois() != null && f.jour() != null) {
            e.setRegle(Evenement.Regle.FIXE);
            e.setMois(f.mois());
            e.setJour(f.jour());
            try { LocalDate.of(2024, f.mois(), f.jour()); }
            catch (RuntimeException ex) { throw ApiException.requeteInvalide("Date impossible : " + f.jour() + "/" + f.mois()); }
        } else {
            throw ApiException.requeteInvalide("Indique une date unique, ou un mois et un jour pour une date fixe chaque année");
        }
        e.setDureeJours(f.dureeJours() == null ? 1 : f.dureeJours());
        e.setSecteurs(nettoyer(f.secteurs()));
        e.setPays(nettoyer(f.pays()));
        e.setAngle(f.angle());
        e.setApproximative(Boolean.TRUE.equals(f.approximative()));
        return vue(repo.save(e));
    }

    @PutMapping("/admin/evenements/{id}/actif")
    public EvenementVue activer(@PathVariable UUID id, @RequestParam boolean actif) {
        Session.courante().exigerSuperAdmin();
        Evenement e = repo.findById(id).orElseThrow(() -> ApiException.introuvable("Événement"));
        e.setActif(actif);
        return vue(repo.save(e));
    }

    @DeleteMapping("/admin/evenements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable UUID id) {
        Session.courante().exigerSuperAdmin();
        repo.deleteById(id);
    }

    private static void verifierPeriode(LocalDate debut, LocalDate fin) {
        if (fin.isBefore(debut) || ChronoUnit.DAYS.between(debut, fin) > 400) {
            throw ApiException.requeteInvalide("Période invalide");
        }
    }

    /** Mots-clés en minuscules, sans accents, séparés par des virgules. */
    private static String nettoyer(String s) {
        if (s == null) return "";
        return String.join(",", java.util.Arrays.stream(CatalogueEvenements.normaliser(s).split("[,;]"))
                .map(String::trim).filter(x -> !x.isEmpty()).toList());
    }

    private static EvenementVue vue(Evenement e) {
        return new EvenementVue(e.getId(), e.getNom(), e.getCategorie().name(), e.getRegle().name(), e.getMois(),
                e.getJour(), e.getNieme(), e.getJourSemaine(), e.getDecalage(), e.getDateUnique(), e.getDureeJours(),
                e.getSecteurs(), e.getPays(), e.getAngle(), e.isApproximative(), e.isActif());
    }
}
