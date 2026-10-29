package com.okpost.calendrier;

import com.okpost.calendrier.CalendrierDtos.CalendrierResume;
import com.okpost.calendrier.CalendrierDtos.CalendrierVue;
import com.okpost.calendrier.CalendrierDtos.NouveauCalendrier;
import com.okpost.calendrier.CalendrierDtos.PublicationPatch;
import com.okpost.calendrier.CalendrierDtos.PublicationVue;
import com.okpost.calendrier.CalendrierDtos.Regeneration;
import com.okpost.client.Client;
import com.okpost.client.ClientRepository;
import com.okpost.commun.ApiException;
import com.okpost.ia.GenerateurIA;
import com.okpost.ia.SelecteurIA;
import com.okpost.securite.Session;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CalendrierController {

    private final CalendrierService service;
    private final GenerationService generation;
    private final SelecteurIA selecteur;
    private final ClientRepository clients;
    private final PublicationRepository publications;

    public CalendrierController(CalendrierService service, GenerationService generation, SelecteurIA selecteur,
                                ClientRepository clients, PublicationRepository publications) {
        this.service = service;
        this.generation = generation;
        this.selecteur = selecteur;
        this.clients = clients;
        this.publications = publications;
    }

    @PostMapping("/clients/{clientId}/calendriers")
    public ResponseEntity<CalendrierVue> creer(@PathVariable UUID clientId, @Valid @RequestBody NouveauCalendrier n) {
        Session s = Session.courante();
        Calendrier c = service.creer(s, clientId, n);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.vue(s, c.getId()));
    }

    @GetMapping("/clients/{clientId}/calendriers")
    public List<CalendrierResume> lister(@PathVariable UUID clientId) {
        return service.lister(Session.courante(), clientId);
    }

    @GetMapping("/calendriers/{id}")
    public CalendrierVue lire(@PathVariable UUID id) {
        return service.vue(Session.courante(), id);
    }

    @PostMapping("/calendriers/{id}/relancer")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void relancer(@PathVariable UUID id) {
        service.relancer(Session.courante(), id);
    }

    @DeleteMapping("/calendriers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable UUID id) {
        service.supprimer(Session.courante(), id);
    }

    @GetMapping("/publications/{id}")
    public PublicationVue lirePublication(@PathVariable UUID id) {
        return service.vuePublication(Session.courante(), id);
    }

    @PatchMapping("/publications/{id}")
    public PublicationVue modifier(@PathVariable UUID id, @RequestBody PublicationPatch patch) {
        return service.modifier(Session.courante(), id, patch);
    }

    /** Régénère un champ (légende, CTA) ou tout le détail, avec une consigne. Appel synchrone (quelques secondes). */
    @PostMapping("/publications/{id}/regenerer")
    public PublicationVue regenerer(@PathVariable UUID id, @Valid @RequestBody Regeneration r) {
        Session s = Session.courante();
        Publication p = service.chargerPublication(s, id);
        if ("tout".equals(r.champ())) {
            generation.redetailler(p, r.consigne());
            return service.vuePublication(s, id);
        }
        Calendrier cal = service.charger(s, p.getCalendrierId());
        Client client = clients.findById(cal.getClientId()).orElseThrow(() -> ApiException.introuvable("Client"));
        GenerateurIA ia = selecteur.pour(s.workspaceId());
        String resume = "Réseau : " + p.getReseau() + "\nFormat : " + p.getFormat() + "\nIdée : " + p.getIdee()
                + "\nLégende : " + p.getLegende() + "\nAppel à l'action : " + p.getCta();
        String texte = ia.regenererChamp(s.workspaceId(), FicheTexte.de(client), cal.getLangue(), resume,
                "legende".equals(r.champ()) ? "légende" : "appel à l'action", r.consigne());
        PublicationPatch patch = "legende".equals(r.champ())
                ? new PublicationPatch(null, null, null, null, null, null, texte, null, null, null, null, null, null)
                : new PublicationPatch(null, null, null, null, null, null, null, null, texte, null, null, null, null);
        return service.modifier(s, id, patch);
    }
}
