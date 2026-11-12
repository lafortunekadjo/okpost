package com.okpost.client;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.okpost.commun.ApiException;

import java.util.concurrent.TimeUnit;

/** Sert les logos et photos. Les noms sont aléatoires (UUID) ; aucun script n'est exécutable. */
@RestController
@RequestMapping("/api/v1/fichiers")
public class FichiersController {

    private final StockageFichiers stockage;
    private final ClientAssetRepository assets;
    private final com.okpost.calendrier.VisuelGenereRepository visuels;

    public FichiersController(StockageFichiers stockage, ClientAssetRepository assets,
                              com.okpost.calendrier.VisuelGenereRepository visuels) {
        this.stockage = stockage;
        this.assets = assets;
        this.visuels = visuels;
    }

    @GetMapping("/{chemin:.+}")
    public ResponseEntity<Resource> lire(@PathVariable String chemin) {
        String mime = assets.findByChemin(chemin).map(ClientAsset::getMime)
                .or(() -> visuels.findByChemin(chemin).map(com.okpost.calendrier.VisuelGenere::getMime))
                .or(() -> chemin.endsWith(".jpg") ? java.util.Optional.of("image/jpeg") : java.util.Optional.empty())
                .orElseThrow(() -> ApiException.introuvable("Fichier"));
        Resource r = stockage.lire(chemin);
        if (!r.exists()) {
            throw ApiException.introuvable("Fichier");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mime))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
                .body(r);
    }
}
