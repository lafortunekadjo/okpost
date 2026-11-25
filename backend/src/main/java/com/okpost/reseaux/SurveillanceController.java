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
        Session s = Session.courante();
        List<Alerte> alertes = surveillance.alertes(s.workspaceId());
        // Une session limitée à un client ne voit que les alertes de ce client
        return s.estCollaborateur() ? alertes.stream().filter(a -> a.clientId().equals(s.clientRestreint())).toList() : alertes;
    }
}
