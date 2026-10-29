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

    public FichiersController(StockageFichiers stockage, ClientAssetRepository assets) {
        this.stockage = stockage;
        this.assets = assets;
    }

    @GetMapping("/{chemin:.+}")
    public ResponseEntity<Resource> lire(@PathVariable String chemin) {
        ClientAsset a = assets.findByChemin(chemin).orElseThrow(() -> ApiException.introuvable("Fichier"));
        Resource r = stockage.lire(chemin);
        if (!r.exists()) {
            throw ApiException.introuvable("Fichier");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.getMime()))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
                .body(r);
    }
}
