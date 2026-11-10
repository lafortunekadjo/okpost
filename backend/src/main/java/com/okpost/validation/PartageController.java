package com.okpost.validation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Calendrier partagé en lecture seule, sans compte : uniquement avec le jeton du lien. */
@RestController
@RequestMapping("/api/v1/public/partage/{jeton}")
public class PartageController {

    private final ValidationService service;

    public PartageController(ValidationService service) {
        this.service = service;
    }

    @GetMapping
    public ValidationDtos.PartageCalendrier lire(@PathVariable String jeton) {
        return service.partage(jeton);
    }
}
