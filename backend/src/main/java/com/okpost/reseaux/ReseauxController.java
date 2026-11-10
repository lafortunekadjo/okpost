package com.okpost.reseaux;

import com.okpost.calendrier.CalendrierDtos.PublicationVue;
import com.okpost.calendrier.CalendrierService;
import com.okpost.calendrier.Publication;
import com.okpost.calendrier.CalendrierRepository;
import com.okpost.client.ClientService;
import com.okpost.config.OkpostProperties;
import com.okpost.securite.Session;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ReseauxController {

    public record JetonManuel(@NotBlank String jeton) {}

    public record Programmation(Instant quand) {}

    private final ComptesSociauxService comptes;
    private final PublicationAutoService auto;
    private final CalendrierService calendriers;
    private final CalendrierRepository depotCalendriers;
    private final com.okpost.calendrier.PublicationRepository publications;
    private final ClientService clients;
    private final MetaApi meta;
    private final OkpostProperties props;

    public ReseauxController(ComptesSociauxService comptes, PublicationAutoService auto, CalendrierService calendriers,
                             CalendrierRepository depotCalendriers, ClientService clients, MetaApi meta, OkpostProperties props,
                             com.okpost.calendrier.PublicationRepository publications) {
        this.publications = publications;
        this.comptes = comptes;
        this.auto = auto;
        this.calendriers = calendriers;
        this.depotCalendriers = depotCalendriers;
        this.clients = clients;
        this.meta = meta;
        this.props = props;
    }

    // ---- Comptes reliés

    @GetMapping("/clients/{id}/comptes-sociaux")
    public Map<String, Object> lister(@PathVariable UUID id) {
        return Map.of("comptes", comptes.lister(Session.courante(), id), "connexionFacebookDisponible", meta.oauthConfigure());
    }

    @GetMapping("/clients/{id}/comptes-sociaux/facebook/connexion")
    public Map<String, String> connexionFacebook(@PathVariable UUID id) {
        return Map.of("url", comptes.urlConnexionFacebook(Session.courante(), id));
    }

    @PostMapping("/clients/{id}/comptes-sociaux/facebook/jeton")
    public List<ComptesSociauxService.CompteVue> jeton(@PathVariable UUID id, @Valid @RequestBody JetonManuel j) {
        return comptes.relierAvecJeton(Session.courante(), id, j.jeton());
    }

    @PostMapping("/clients/{id}/comptes-sociaux/{compteId}/activer")
    public List<ComptesSociauxService.CompteVue> activer(@PathVariable UUID id, @PathVariable UUID compteId) {
        return comptes.activer(Session.courante(), id, compteId);
    }

    @DeleteMapping("/clients/{id}/comptes-sociaux/{compteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable UUID id, @PathVariable UUID compteId) {
        comptes.supprimer(Session.courante(), id, compteId);
    }

    /** Retour de la fenêtre Facebook (public : la sécurité repose sur l'état signé). */
    @GetMapping("/public/meta/retour")
    public ResponseEntity<Void> retour(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
                                       @RequestParam(name = "error_description", required = false) String erreur) {
        String cible;
        try {
            if (code == null) throw new IllegalStateException(erreur == null ? "Connexion annulée" : erreur);
            UUID clientId = comptes.retourFacebook(code, state);
            cible = props.urlPublique() + "/clients/" + clientId + "?reseaux=ok";
        } catch (RuntimeException e) {
            cible = props.urlPublique() + "/?reseaux=erreur&message="
                    + java.net.URLEncoder.encode(String.valueOf(e.getMessage()), java.nio.charset.StandardCharsets.UTF_8);
        }
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, URI.create(cible).toString()).build();
    }

    // ---- Programmation des publications

    @PostMapping("/publications/{id}/programmer")
    @Transactional
    public PublicationVue programmer(@PathVariable UUID id, @RequestBody(required = false) Programmation prog) {
        Session s = Session.courante();
        Publication p = calendriers.chargerPublication(s, id);
        var cal = calendriers.charger(s, p.getCalendrierId());
        auto.programmer(p, clients.charger(s, cal.getClientId()), prog == null ? null : prog.quand());
        return calendriers.vuePublication(s, id);
    }

    /** Programme toutes les publications validées du calendrier et dit pourquoi les autres ne le sont pas. */
    @PostMapping("/calendriers/{id}/programmer-validees")
    @Transactional
    public PublicationAutoService.BilanProgrammation programmerValidees(@PathVariable UUID id) {
        Session s = Session.courante();
        var cal = calendriers.charger(s, id);
        var client = clients.charger(s, cal.getClientId());
        List<Publication> pubs = publications.findByCalendrierIdOrderByDatePrevueAscReseauAsc(id);
        return auto.programmerValidees(pubs, client);
    }

    @PostMapping("/publications/{id}/annuler-programmation")
    @Transactional
    public PublicationVue annuler(@PathVariable UUID id) {
        Session s = Session.courante();
        auto.annuler(calendriers.chargerPublication(s, id));
        return calendriers.vuePublication(s, id);
    }

    @PostMapping("/publications/{id}/publier-maintenant")
    public PublicationVue publierMaintenant(@PathVariable UUID id) {
        Session s = Session.courante();
        Publication p = calendriers.chargerPublication(s, id);
        if (p.getStatut() != com.okpost.calendrier.StatutPublication.PROGRAMMEE) {
            programmer(id, null);
        }
        auto.publier(id);
        return calendriers.vuePublication(s, id);
    }
}
