package com.okpost.validation;

import com.okpost.client.Client;
import com.okpost.client.ClientService;
import com.okpost.commun.ApiException;
import com.okpost.securite.Session;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Codes personnels de validation. Si un client a au moins une personne autorisée active, le portail exige un code :
 * chaque décision garde alors le nom de la personne qui l'a prise. Sans personne autorisée, le lien secret suffit comme avant.
 */
@Service
public class CodesValidationService {

    public record ContactVue(UUID id, String nom, boolean actif, Instant derniereUtilisation, Instant creeLe) {}

    /** Le code en clair n'est montré qu'à la création ou au changement : ensuite seule son empreinte est conservée. */
    public record ContactAvecCode(ContactVue contact, String code) {}

    public record Validateur(UUID id, String nom) {}

    static final int ESSAIS_MAX = 5;
    static final long BLOCAGE_MS = 15 * 60_000L;
    private static final SecureRandom ALEA = new SecureRandom();

    private final ContactValidationRepository contacts;
    private final ClientService clients;
    private final PasswordEncoder encodeur;
    private final Map<String, ArrayDeque<Long>> echecs = new ConcurrentHashMap<>();

    public CodesValidationService(ContactValidationRepository contacts, ClientService clients, PasswordEncoder encodeur) {
        this.contacts = contacts;
        this.clients = clients;
        this.encodeur = encodeur;
    }

    // ------------------------------------------------------------ côté community manager

    @Transactional(readOnly = true)
    public List<ContactVue> lister(Session s, UUID clientId) {
        clients.charger(s, clientId);
        return contacts.findByClientIdOrderByNom(clientId).stream().map(CodesValidationService::vue).toList();
    }

    @Transactional
    public ContactAvecCode creer(Session s, UUID clientId, String nom) {
        Client c = clients.charger(s, clientId);
        if (nom == null || nom.isBlank()) throw ApiException.requeteInvalide("Le nom de la personne est obligatoire");
        if (contacts.findByClientIdOrderByNom(clientId).size() >= 10) {
            throw ApiException.requeteInvalide("10 personnes autorisées au plus par client");
        }
        ContactValidation v = new ContactValidation();
        v.setWorkspaceId(s.workspaceId());
        v.setClientId(c.getId());
        v.setNom(nom.trim());
        String code = nouveauCode(clientId, null);
        v.setCodeHash(encodeur.encode(code));
        return new ContactAvecCode(vue(contacts.save(v)), code);
    }

    @Transactional
    public ContactAvecCode renouveler(Session s, UUID clientId, UUID contactId) {
        clients.charger(s, clientId);
        ContactValidation v = contacts.findByIdAndClientId(contactId, clientId).orElseThrow(() -> ApiException.introuvable("Personne"));
        String code = nouveauCode(clientId, contactId);
        v.setCodeHash(encodeur.encode(code));
        v.setActif(true);
        return new ContactAvecCode(vue(v), code);
    }

    @Transactional
    public void supprimer(Session s, UUID clientId, UUID contactId) {
        clients.charger(s, clientId);
        contacts.delete(contacts.findByIdAndClientId(contactId, clientId).orElseThrow(() -> ApiException.introuvable("Personne")));
    }

    /** Code à 6 chiffres, différent de celui des autres personnes du même client (sinon la personne ne serait pas identifiable). */
    private String nouveauCode(UUID clientId, UUID sauf) {
        List<ContactValidation> autres = contacts.findByClientIdOrderByNom(clientId).stream()
                .filter(x -> !x.getId().equals(sauf)).toList();
        for (int i = 0; i < 50; i++) {
            String code = String.format("%06d", ALEA.nextInt(1_000_000));
            if (autres.stream().noneMatch(x -> encodeur.matches(code, x.getCodeHash()))) return code;
        }
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Impossible de générer un code unique");
    }

    // ------------------------------------------------------------ côté client (portail public)

    @Transactional(readOnly = true)
    public boolean codeRequis(UUID clientId) {
        return !contacts.findByClientIdAndActifTrue(clientId).isEmpty();
    }

    /**
     * Identifie la personne qui a saisi ce code. Après {@value #ESSAIS_MAX} codes faux sur un même lien, il est bloqué 15 minutes
     * (un code à 6 chiffres ne doit pas pouvoir être deviné). Renvoie vide si le client n'exige pas de code.
     */
    @Transactional
    public Optional<Validateur> verifier(String jeton, UUID clientId, String code) {
        List<ContactValidation> actifs = contacts.findByClientIdAndActifTrue(clientId);
        if (actifs.isEmpty()) return Optional.empty();
        verifierBlocage(jeton);
        if (code != null && code.trim().matches("\\d{6}")) {
            for (ContactValidation v : actifs) {
                if (encodeur.matches(code.trim(), v.getCodeHash())) {
                    echecs.remove(jeton);
                    v.setDerniereUtilisation(Instant.now());
                    return Optional.of(new Validateur(v.getId(), v.getNom()));
                }
            }
        }
        enregistrerEchec(jeton);
        throw ApiException.interdit("Code incorrect");
    }

    private void verifierBlocage(String jeton) {
        ArrayDeque<Long> f = echecs.get(jeton);
        if (f == null) return;
        synchronized (f) {
            long limite = System.currentTimeMillis() - BLOCAGE_MS;
            while (!f.isEmpty() && f.peekFirst() < limite) f.pollFirst();
            if (f.size() >= ESSAIS_MAX) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                        "Trop de codes incorrects : réessaie dans 15 minutes ou demande un nouveau code à ton community manager.");
            }
        }
    }

    private void enregistrerEchec(String jeton) {
        ArrayDeque<Long> f = echecs.computeIfAbsent(jeton, k -> new ArrayDeque<>());
        synchronized (f) {
            f.addLast(System.currentTimeMillis());
        }
    }

    private static ContactVue vue(ContactValidation v) {
        return new ContactVue(v.getId(), v.getNom(), v.isActif(), v.getDerniereUtilisation(), v.getCreatedAt());
    }
}
