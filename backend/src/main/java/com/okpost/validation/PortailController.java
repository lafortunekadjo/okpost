package com.okpost.validation;

import com.okpost.securite.JwtService;
import com.okpost.validation.ValidationDtos.Decision;
import com.okpost.validation.ValidationDtos.NouvelleLegende;
import com.okpost.validation.ValidationDtos.Portail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Portail de validation : accessible sans compte, uniquement avec le jeton du lien. */
@RestController
@RequestMapping("/api/v1/public/validation/{jeton}")
public class PortailController {

    private final ValidationService service;

    public PortailController(ValidationService service) {
        this.service = service;
    }

    @GetMapping
    public Portail lire(@PathVariable String jeton, @RequestHeader(value = "X-Code-Validation", required = false) String code) {
        return service.portail(jeton, code);
    }

    @PostMapping("/publications/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decider(@PathVariable String jeton, @PathVariable UUID id, @Valid @RequestBody Decision d,
                        @RequestHeader(value = "X-Code-Validation", required = false) String code, HttpServletRequest req) {
        service.decider(jeton, id, d.decision(), d.commentaire(), ipHash(req), code);
    }

    @PutMapping("/publications/{id}/legende")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void modifierLegende(@PathVariable String jeton, @PathVariable UUID id, @Valid @RequestBody NouvelleLegende n,
                                @RequestHeader(value = "X-Code-Validation", required = false) String code, HttpServletRequest req) {
        service.modifierLegende(jeton, id, n.legende(), n.commentaire(), ipHash(req), code);
    }

    private static String ipHash(HttpServletRequest req) {
        return JwtService.sha256("okpost:" + req.getRemoteAddr());
    }
}
