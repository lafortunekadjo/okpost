package com.okpost.client;

import com.okpost.abonnement.AbonnementService;
import com.okpost.calendrier.FicheTexte;
import com.okpost.commun.ApiException;
import com.okpost.ia.GenerationException;
import com.okpost.ia.SelecteurIA;
import com.okpost.securite.Session;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Corrige, reformule ou raccourcit un texte de la fiche client (description d'un produit ou de l'activité) avec l'IA. */
@Service
public class ReformulationService {

    static final int TEXTE_MAX = 2000;
    private static final int APPELS_PAR_HEURE = 60;

    static final Map<String, String> MODES = Map.of(
            "CORRIGER", "Corrige uniquement l'orthographe, la grammaire, la ponctuation et les majuscules. Ne change ni les mots choisis, ni le style, ni la longueur.",
            "REFORMULER", "Reformule le texte : mêmes informations, phrases plus claires, fluides et agréables à lire, dans le ton de la marque. N'ajoute aucune information.",
            "RACCOURCIR", "Raccourcis le texte d'environ un tiers en gardant l'essentiel, sans rien inventer.");

    private final ClientService clients;
    private final SelecteurIA selecteur;
    private final AbonnementService abonnements;
    private final Map<UUID, ArrayDeque<Instant>> appels = new ConcurrentHashMap<>();

    public ReformulationService(ClientService clients, SelecteurIA selecteur, AbonnementService abonnements) {
        this.clients = clients;
        this.selecteur = selecteur;
        this.abonnements = abonnements;
    }

    /**
     * @param clientId  client dont la fiche donne le contexte (ton, langue), ou null pour une fiche pas encore enregistrée
     * @param longueurMax nombre de caractères que le champ accepte (le résultat est ramené à cette taille)
     */
    public String reformuler(Session s, UUID clientId, String texte, String sujet, String mode, int longueurMax) {
        abonnements.exigerEcriture(s.workspaceId());
        if (texte == null || texte.isBlank()) throw ApiException.requeteInvalide("Écris d'abord quelques mots à améliorer");
        if (texte.length() > TEXTE_MAX) throw ApiException.requeteInvalide("Texte trop long (" + TEXTE_MAX + " caractères au plus)");
        String m = mode == null ? "REFORMULER" : mode.trim().toUpperCase(Locale.ROOT);
        String instruction = MODES.get(m);
        if (instruction == null) throw ApiException.requeteInvalide("Mode inconnu : " + mode);
        limiter(s.workspaceId());

        String fiche = "Aucune fiche enregistrée pour l'instant.";
        String langue = "fr";
        if (clientId != null) {
            Client c = clients.charger(s, clientId);
            fiche = FicheTexte.de(c);
            langue = c.getLangue();
        }
        try {
            String brut = selecteur.pour(s.workspaceId()).reformulerTexte(s.workspaceId(), fiche, langue, texte.trim(),
                    sujet == null ? null : sujet.trim(), instruction, longueurMax);
            String propre = nettoyerSortie(brut, longueurMax);
            if (propre.isEmpty()) throw new ApiException(HttpStatus.BAD_GATEWAY, "L'IA n'a pas renvoyé de texte : réessaie");
            return propre;
        } catch (GenerationException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    /** Retire les guillemets ou repères que l'IA ajoute parfois, et ramène le texte à la taille du champ sans couper un mot. */
    static String nettoyerSortie(String brut, int max) {
        if (brut == null) return "";
        String t = brut.trim().replaceAll("[\\p{Cntrl}&&[^\\n]]", "");
        t = t.replaceAll("^(<<<|\"|«|“)\\s*", "").replaceAll("\\s*(>>>|\"|»|”)$", "").trim();
        if (t.length() <= max) return t;
        String coupe = t.substring(0, max);
        int espace = coupe.lastIndexOf(' ');
        return (espace > max * 0.6 ? coupe.substring(0, espace) : coupe).trim();
    }

    private void limiter(UUID workspaceId) {
        ArrayDeque<Instant> file = appels.computeIfAbsent(workspaceId, k -> new ArrayDeque<>());
        synchronized (file) {
            Instant limite = Instant.now().minus(Duration.ofHours(1));
            while (!file.isEmpty() && file.peekFirst().isBefore(limite)) file.pollFirst();
            if (file.size() >= APPELS_PAR_HEURE) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Trop de reformulations : réessaie dans quelques minutes");
            }
            file.addLast(Instant.now());
        }
    }
}
