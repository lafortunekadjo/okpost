package com.okpost.reseaux;

import com.okpost.config.OkpostProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Chiffre les jetons des réseaux sociaux (AES-256-GCM). Ils ne sont jamais stockés ni renvoyés en clair. */
@Component
public class Chiffreur {

    private static final Logger log = LoggerFactory.getLogger(Chiffreur.class);
    private static final SecureRandom ALEA = new SecureRandom();

    private final SecretKeySpec cle;

    public Chiffreur(OkpostProperties props) {
        String source = props.cleChiffrement();
        if (source == null || source.isBlank()) {
            log.warn("OKPOST_CLE_CHIFFREMENT absente : clé dérivée de JWT_SECRET. Définis-la avant la production et ne la change plus.");
            source = "okpost-jetons:" + props.jwt().secret();
        }
        try {
            byte[] octets = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            this.cle = new SecretKeySpec(octets, "AES");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String chiffrer(String clair) {
        try {
            byte[] iv = new byte[12];
            ALEA.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, cle, new GCMParameterSpec(128, iv));
            byte[] chiffre = c.doFinal(clair.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + chiffre.length).put(iv).put(chiffre).array());
        } catch (Exception e) {
            throw new IllegalStateException("Chiffrement impossible", e);
        }
    }

    public String dechiffrer(String valeur) {
        try {
            byte[] tout = Base64.getDecoder().decode(valeur);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, cle, new GCMParameterSpec(128, tout, 0, 12));
            return new String(c.doFinal(tout, 12, tout.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Jeton illisible : la clé de chiffrement a-t-elle changé ? Reliez la Page à nouveau.", e);
        }
    }
}
