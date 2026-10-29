package com.okpost.securite;

import com.okpost.config.OkpostProperties;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    static final String COOKIE = "okpost_refresh";

    public record Inscription(@NotBlank @Size(max = 150) String nomEspace,
                              @NotBlank @Size(max = 150) String nom,
                              @NotBlank @Email String email,
                              @NotBlank @Size(min = 8, max = 100) String motDePasse) {}

    public record Connexion(@NotBlank @Email String email, @NotBlank String motDePasse) {}

    public record Reponse(String jetonAcces, UUID userId, UUID workspaceId, String nom, String email, String role) {}

    private final AuthService service;
    private final OkpostProperties props;

    public AuthController(AuthService service, OkpostProperties props) {
        this.service = service;
        this.props = props;
    }

    @PostMapping("/inscription")
    public Reponse inscription(@Valid @RequestBody Inscription i, HttpServletResponse rep) {
        return repondre(service.inscrire(i.nomEspace(), i.nom(), i.email(), i.motDePasse()), rep);
    }

    @PostMapping("/connexion")
    public Reponse connexion(@Valid @RequestBody Connexion c, HttpServletResponse rep) {
        return repondre(service.connecter(c.email(), c.motDePasse()), rep);
    }

    @PostMapping("/rafraichir")
    public Reponse rafraichir(@CookieValue(name = COOKIE, required = false) String jeton, HttpServletResponse rep) {
        if (jeton == null) {
            throw new com.okpost.commun.ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Session expirée");
        }
        return repondre(service.rafraichir(jeton), rep);
    }

    @PostMapping("/deconnexion")
    public void deconnexion(@CookieValue(name = COOKIE, required = false) String jeton, HttpServletResponse rep) {
        service.deconnecter(jeton);
        rep.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    private Reponse repondre(AuthService.Jetons j, HttpServletResponse rep) {
        rep.addHeader(HttpHeaders.SET_COOKIE,
                cookie(j.rafraichissement(), Duration.ofDays(props.jwt().rafraichissementJours())).toString());
        Utilisateur u = j.utilisateur();
        return new Reponse(j.acces(), u.getId(), u.getWorkspaceId(), u.getNom(), u.getEmail(), u.getRole().name());
    }

    private ResponseCookie cookie(String valeur, Duration duree) {
        return ResponseCookie.from(COOKIE, valeur)
                .httpOnly(true)
                .secure(props.urlPublique().startsWith("https"))
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(duree)
                .build();
    }
}
