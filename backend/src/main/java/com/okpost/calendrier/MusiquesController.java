package com.okpost.calendrier;

import com.okpost.calendrier.MusiqueService.Piste;
import com.okpost.securite.Session;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Bibliothèque de musiques : tout compte connecté peut les écouter et les utiliser ; seul le super-admin les gère. */
@RestController
@RequestMapping("/api/v1/musiques")
public class MusiquesController {

    public record PisteVue(String id, String nom, long taille) {
        static PisteVue de(Piste p) {
            return new PisteVue(p.id(), p.nom(), p.taille());
        }
    }

    private final MusiqueService musiques;

    public MusiquesController(MusiqueService musiques) {
        this.musiques = musiques;
    }

    @GetMapping
    public List<PisteVue> lister() {
        Session.courante();
        return musiques.lister().stream().map(PisteVue::de).toList();
    }

    @GetMapping("/{id}/ecoute")
    public ResponseEntity<Resource> ecouter(@PathVariable String id) {
        Session.courante();
        Piste p = musiques.resoudre(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(MusiqueService.typeMime(p.chemin().getFileName().toString())))
                .cacheControl(CacheControl.maxAge(1, java.util.concurrent.TimeUnit.HOURS).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(musiques.ecoute(p));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PisteVue ajouter(@RequestParam("fichier") MultipartFile fichier) {
        Session.courante().exigerSuperAdmin();
        return PisteVue.de(musiques.ajouter(fichier));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable String id) {
        Session.courante().exigerSuperAdmin();
        musiques.supprimer(id);
    }
}
