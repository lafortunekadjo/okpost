package com.okpost.analyse;

import com.okpost.calendrier.FicheTexte;
import com.okpost.client.Client;
import com.okpost.client.ClientService;
import com.okpost.commun.ApiException;
import com.okpost.ia.Modeles.Recommandations;
import com.okpost.ia.SelecteurIA;
import com.okpost.reseaux.MetaApi;
import com.okpost.securite.Session;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients/{clientId}")
public class BilanController {

    public record Periode(@NotNull LocalDate debut, @NotNull LocalDate fin) {}

    public record EtatPage(boolean pageReliee) {}

    public record ApercuAnalyse(String texte) {}

    private final AnalysePageService analyse;
    private final ClientService clients;
    private final SelecteurIA selecteur;

    public BilanController(AnalysePageService analyse, ClientService clients, SelecteurIA selecteur) {
        this.analyse = analyse;
        this.clients = clients;
        this.selecteur = selecteur;
    }

    @GetMapping("/page")
    public EtatPage etat(@PathVariable UUID clientId) {
        Client c = clients.charger(Session.courante(), clientId);
        return new EtatPage(analyse.pageReliee(c.getId()));
    }

    /** Ce que l'IA apprendra de la Page (90 derniers jours), sans lancer de calendrier. */
    @GetMapping("/analyse-page")
    public ApercuAnalyse apercu(@PathVariable UUID clientId) {
        Client c = clients.charger(Session.courante(), clientId);
        String t = analyse.resumePourCalendrier(c);
        if (t == null) throw ApiException.requeteInvalide("Page Facebook non reliée ou illisible pour le moment");
        return new ApercuAnalyse(t);
    }

    @GetMapping("/bilan")
    public AnalysePageService.Bilan bilan(@PathVariable UUID clientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        verifier(debut, fin);
        Client c = clients.charger(Session.courante(), clientId);
        try {
            return analyse.bilan(c, debut, fin);
        } catch (MetaApi.MetaException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    @PostMapping("/bilan/recommandations")
    public Recommandations recommandations(@PathVariable UUID clientId, @Valid @RequestBody Periode p) {
        verifier(p.debut(), p.fin());
        Session s = Session.courante();
        Client c = clients.charger(s, clientId);
        AnalysePageService.Bilan b;
        try {
            b = analyse.bilan(c, p.debut(), p.fin());
        } catch (MetaApi.MetaException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
        return selecteur.pour(s.workspaceId()).recommandations(s.workspaceId(), FicheTexte.de(c), analyse.texteBilan(b));
    }

    private static void verifier(LocalDate debut, LocalDate fin) {
        if (fin.isBefore(debut)) throw ApiException.requeteInvalide("La date de fin doit suivre la date de début");
        if (ChronoUnit.DAYS.between(debut, fin) > 186) throw ApiException.requeteInvalide("Période trop longue : 6 mois maximum");
        if (fin.isAfter(LocalDate.now().plusDays(1))) throw ApiException.requeteInvalide("La période ne peut pas être dans le futur");
    }
}
