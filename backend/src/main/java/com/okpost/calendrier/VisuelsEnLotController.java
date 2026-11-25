package com.okpost.calendrier;

import com.okpost.calendrier.VisuelsEnLotService.Progression;
import com.okpost.securite.Session;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/calendriers/{id}/visuels-en-lot")
public class VisuelsEnLotController {

    /** remplacer : refaire aussi les publications qui ont déjà un visuel ; discret : texte plus petit sur les affiches. */
    public record Demande(Boolean remplacer, Boolean discret) {}

    private final VisuelsEnLotService service;

    public VisuelsEnLotController(VisuelsEnLotService service) {
        this.service = service;
    }

    /** Lance la génération en arrière-plan : la réponse indique combien de publications sont concernées. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Progression demarrer(@PathVariable UUID id, @RequestBody(required = false) Demande d) {
        return service.demarrer(Session.courante(), id, d != null && Boolean.TRUE.equals(d.remplacer()),
                d != null && Boolean.TRUE.equals(d.discret()));
    }

    /** Avancement de la dernière génération de ce calendrier (204 s'il n'y en a pas eu depuis le dernier redémarrage). */
    @GetMapping
    public ResponseEntity<Progression> etat(@PathVariable UUID id) {
        return service.etat(Session.courante(), id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
}
