package com.okpost.securite;

import com.okpost.config.OkpostProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class JwtService {

    private static final SecureRandom ALEA = new SecureRandom();

    private final JwtEncoder encodeur;
    private final OkpostProperties props;

    public JwtService(JwtEncoder encodeur, OkpostProperties props) {
        this.encodeur = encodeur;
        this.props = props;
    }

    /** Session limitée à un client d'un autre espace : même durée de vie qu'un jeton normal, rôle COLLAB, un seul client autorisé. */
    public String jetonContexte(Utilisateur u, java.util.UUID espaceId, java.util.UUID clientId) {
        Instant maintenant = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("okpost")
                .subject(u.getId().toString())
                .issuedAt(maintenant)
                .expiresAt(maintenant.plus(Duration.ofMinutes(props.jwt().accesMinutes())))
                .claim("ws", espaceId.toString())
                .claim("role", Role.COLLAB.name())
                .claim("client", clientId.toString())
                .claim("nom", u.getNom())
                .build();
        JwsHeader entete = JwsHeader.with(MacAlgorithm.HS256).build();
        return encodeur.encode(JwtEncoderParameters.from(entete, claims)).getTokenValue();
    }

    public String jetonAcces(Utilisateur u) {
        Instant maintenant = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("okpost")
                .subject(u.getId().toString())
                .issuedAt(maintenant)
                .expiresAt(maintenant.plus(Duration.ofMinutes(props.jwt().accesMinutes())))
                .claim("ws", u.getWorkspaceId().toString())
                .claim("role", u.getRole().name())
                .claim("nom", u.getNom())
                .build();
        JwsHeader entete = JwsHeader.with(MacAlgorithm.HS256).build();
        return encodeur.encode(JwtEncoderParameters.from(entete, claims)).getTokenValue();
    }

    /** Jeton aléatoire de 32 octets, encodé en base64 URL (liens de validation, rafraîchissement). */
    public static String jetonAleatoire() {
        byte[] octets = new byte[32];
        ALEA.nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }

    public static String sha256(String valeur) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(valeur.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
