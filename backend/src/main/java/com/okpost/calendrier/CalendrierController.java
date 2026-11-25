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
    private final VisuelService visuels;
    private final MontageService montage;

    public CalendrierController(CalendrierService service, GenerationService generation, SelecteurIA selecteur,
                                ClientRepository clients, PublicationRepository publications, VisuelService visuels,
                                MontageService montage) {
        this.visuels = visuels;
        this.montage = montage;
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

    /** Carrousel des photos du produit mis en avant, chacune avec le logo en filigrane. */
    @PostMapping("/publications/{id}/carrousel-produit")
    public PublicationVue carrouselProduit(@PathVariable UUID id) {
        visuels.carrouselProduit(Session.courante(), id);
        return service.vuePublication(Session.courante(), id);
    }

    @PostMapping("/calendriers/{id}/archiver")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiver(@PathVariable UUID id) {
        service.archiver(Session.courante(), id);
    }

    @PostMapping("/calendriers/{id}/restaurer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restaurer(@PathVariable UUID id) {
        service.restaurer(Session.courante(), id);
    }

    @DeleteMapping("/publications/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimerPublication(@PathVariable UUID id) {
        service.supprimerPublication(Session.courante(), id);
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
    /** Génère une image à partir du prompt (celui du brief par défaut). Payant chez le fournisseur. */
    @PostMapping("/publications/{id}/visuels")
    @ResponseStatus(HttpStatus.CREATED)
    public CalendrierDtos.VisuelVue genererImage(@PathVariable UUID id, @Valid @RequestBody CalendrierDtos.DemandeImage d) {
        return visuels.generer(Session.courante(), id, d.prompt(), d.ratio(), !Boolean.FALSE.equals(d.habiller()),
                new VisuelService.Saisie(d.accroche(), d.gabarit(), d.chiffre(), d.texte(), d.cta(), d.photo(), d.discret()), d.moteur(), d.diapositive());
    }

    /** Photo ou vidéo téléversée par le CM (sans génération). */
    @PostMapping("/publications/{id}/medias")
    @ResponseStatus(HttpStatus.CREATED)
    public CalendrierDtos.VisuelVue televerser(@PathVariable UUID id,
                                              @org.springframework.web.bind.annotation.RequestParam("fichier")
                                              org.springframework.web.multipart.MultipartFile fichier,
                                              @org.springframework.web.bind.annotation.RequestParam(value = "diapositive", required = false)
                                              Integer diapositive) {
        return visuels.televerser(Session.courante(), id, fichier, diapositive);
    }

    /** Vidéo défilante à partir des visuels du carrousel (ffmpeg, 5 à 30 secondes de calcul). */
    @PostMapping("/publications/{id}/montage")
    @ResponseStatus(HttpStatus.CREATED)
    public CalendrierDtos.VisuelVue monter(@PathVariable UUID id,
            @org.springframework.web.bind.annotation.RequestParam(value = "secondes", required = false) Integer secondes,
            @org.springframework.web.bind.annotation.RequestParam(value = "transition", required = false) String transition,
            @org.springframework.web.bind.annotation.RequestParam(value = "musique", required = false)
            org.springframework.web.multipart.MultipartFile musique,
            @org.springframework.web.bind.annotation.RequestParam(value = "musiqueId", required = false) String musiqueId) {
        return montage.monter(Session.courante(), id, secondes, transition, musique, musiqueId);
    }

    /** Carrousel sur Facebook : vidéo défilante (VIDEO) ou plusieurs photos dans une publication (PHOTOS). */
    @org.springframework.web.bind.annotation.PutMapping("/publications/{id}/mode-carrousel")
    public PublicationVue modeCarrousel(@PathVariable UUID id, @Valid @RequestBody CalendrierDtos.ModeCarrousel m) {
        Session s = Session.courante();
        Publication p = service.chargerPublication(s, id);
        p.setModeCarrousel(m.mode());
        publications.save(p);
        return service.vuePublication(s, id);
    }

    @DeleteMapping("/publications/{id}/visuels/{visuelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimerImage(@PathVariable UUID id, @PathVariable UUID visuelId) {
        visuels.supprimer(Session.courante(), id, visuelId);
    }

    /** Met la même publication sur d'autres réseaux (une copie par réseau). */
    @PostMapping("/publications/{id}/dupliquer")
    public CalendrierDtos.ResultatDuplication dupliquer(@PathVariable UUID id, @Valid @RequestBody CalendrierDtos.DemandeDuplication d) {
        return adapterTextes(service.dupliquer(Session.courante(), id, d.reseaux()), Boolean.TRUE.equals(d.adapter()));
    }

    /** Même chose pour plusieurs publications cochées dans le calendrier. */
    @PostMapping("/calendriers/{id}/dupliquer")
    public CalendrierDtos.ResultatDuplication dupliquerSelection(@PathVariable UUID id,
                                                                  @Valid @RequestBody CalendrierDtos.DemandeDuplicationSelection d) {
        return adapterTextes(service.dupliquerSelection(Session.courante(), id, d.ids(), d.reseaux()), Boolean.TRUE.equals(d.adapter()));
    }

    private static final int ADAPTATIONS_MAX = 4;

    /** L'IA réécrit la légende de chaque copie pour son réseau (longueur, ton, hashtags). Limité : chaque réécriture est un appel d'IA. */
    private CalendrierDtos.ResultatDuplication adapterTextes(CalendrierDtos.ResultatDuplication r, boolean adapter) {
        if (!adapter || r.creees().isEmpty()) return r;
        Session s = Session.courante();
        java.util.List<String> ignorees = new java.util.ArrayList<>(r.ignorees());
        int adaptees = 0;
        for (UUID id : r.creees()) {
            if (adaptees >= ADAPTATIONS_MAX) {
                ignorees.add("Adaptation par l'IA limitée à " + ADAPTATIONS_MAX + " publications par demande : les autres gardent le texte d'origine");
                break;
            }
            try {
                Publication p = service.chargerPublication(s, id);
                generation.redetailler(p, "Adapte cette publication au réseau " + p.getReseau()
                        + " (longueur, ton et hashtags propres à ce réseau) sans changer l'idée ni le sujet.");
                adaptees++;
            } catch (RuntimeException e) {
                ignorees.add("Adaptation par l'IA impossible (" + e.getMessage() + ") : les copies gardent le texte d'origine");
                break;
            }
        }
        return new CalendrierDtos.ResultatDuplication(r.creees(), ignorees, adaptees);
    }

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
                ? new PublicationPatch(null, null, null, null, null, null, texte, null, null, null, null, null, null, null)
                : new PublicationPatch(null, null, null, null, null, null, null, null, texte, null, null, null, null, null);
        return service.modifier(s, id, patch);
    }
}
