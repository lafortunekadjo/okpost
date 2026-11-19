package com.okpost.reseaux;

import com.okpost.reseaux.SurveillanceService.Alerte;
import com.okpost.securite.Session;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/surveillance")
public class SurveillanceController {

    private final SurveillanceService surveillance;

    public SurveillanceController(SurveillanceService surveillance) {
        this.surveillance = surveillance;
    }

    /** Alertes du tableau de bord : ce qui ne partira pas ou a échoué. */
    @GetMapping
    public List<Alerte> alertes() {
        return surveillance.alertes(Session.courante().workspaceId());
    }
}
