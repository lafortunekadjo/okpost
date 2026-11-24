package com.okpost.client;

import com.okpost.abonnement.AbonnementService;
import com.okpost.client.ExtracteurSite.ContenuSite;
import com.okpost.commun.ApiException;
import com.okpost.ia.GenerationException;
import com.okpost.ia.Modeles.FicheSite;
import com.okpost.ia.SelecteurIA;
import com.okpost.securite.Session;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Propose une fiche client à partir du site web : lecture du site, analyse par l'IA, puis nettoyage du résultat.
 * Rien n'est enregistré : l'utilisateur relit, corrige et enregistre lui-même la fiche.
 */
@Service
public class FicheDepuisSiteService {

    /** Champs proposés (mêmes noms que la fiche), plus les pistes de logo, les pages lues et les avertissements. */
    public record FicheProposee(String nom, String secteur, String description, String slogan, Integer anneeCreation,
                                String ville, String pays, String zone, String adresse, String horaires, String langue,
                                String ton, Boolean tutoiement, List<Client.Offre> offres, List<String> cibles,
                                List<String> couleurs, String domaine, Map<String, String> reseaux, String whatsapp,
                                List<String> logos, List<String> photos, List<String> sources, List<String> avertissements) {}

    /**
     * Ce qui est lu tel quel à la source (site, Page, compte) : il prime sur l'IA. Les champs d'adresse, de ville, de pays et d'horaires
     * ne sont imposés que s'ils sont renseignés.
     */
    public record Faits(List<String> couleurs, Map<String, String> reseaux, String whatsapp, String domaine, List<String> logos,
                        List<String> photos, List<String> sources, List<String> avertissements,
                        String adresse, String ville, String pays, String horaires) {}

    private static final int ANALYSES_PAR_HEURE = 15;

    private final LecteurSite lecteur;
    private final SelecteurIA selecteur;
    private final AbonnementService abonnements;
    private final Map<UUID, ArrayDeque<Instant>> appels = new ConcurrentHashMap<>();

    public FicheDepuisSiteService(LecteurSite lecteur, SelecteurIA selecteur, AbonnementService abonnements) {
        this.lecteur = lecteur;
        this.selecteur = selecteur;
        this.abonnements = abonnements;
    }

    /** @param seulementImages ne cherche que le logo et les photos du site, sans appeler l'IA (rapide et gratuit) */
    public FicheProposee proposer(Session s, String url, boolean seulementImages) {
        abonnements.exigerEcriture(s.workspaceId());
        limiter(s.workspaceId());
        ContenuSite site = lecteur.lire(url);
        if (seulementImages) return assembler(site, null);
        FicheSite ia;
        try {
            ia = selecteur.pour(s.workspaceId()).analyserSite(s.workspaceId(), site.pourIa());
        } catch (GenerationException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
        return assembler(site, ia);
    }

    /** Au plus 15 analyses par heure et par espace : chacune appelle l'IA et lit un site externe. */
    public void limiter(UUID workspaceId) {
        ArrayDeque<Instant> file = appels.computeIfAbsent(workspaceId, k -> new ArrayDeque<>());
        synchronized (file) {
            Instant limite = Instant.now().minus(Duration.ofHours(1));
            while (!file.isEmpty() && file.peekFirst().isBefore(limite)) file.pollFirst();
            if (file.size() >= ANALYSES_PAR_HEURE) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Trop d'analyses de sites : réessaie dans quelques minutes");
            }
            file.addLast(Instant.now());
        }
    }

    /** Fiche proposée à partir d'un site web. */
    static FicheProposee assembler(ContenuSite site, FicheSite ia) {
        List<String> avertissements = new ArrayList<>();
        if (site.texte().replaceAll("--- Page : [^\\n]* ---", "").trim().length() < 400) {
            avertissements.add("Le site contient très peu de texte lisible (il charge peut-être son contenu avec JavaScript) : la fiche proposée est incomplète.");
        }
        return construire(ia, new Faits(site.couleurs(), site.reseaux(), site.whatsapp(), domaine(site.url()), site.logos(), site.photos(),
                site.pagesLues(), avertissements, null, null, null, null));
    }

    /** Ce qui est lu directement à la source prime sur l'IA ; le reste est nettoyé et plafonné. */
    public static FicheProposee construire(FicheSite ia, Faits faits) {
        FicheSite f = ia == null ? new FicheSite(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null) : ia;

        Set<String> couleurs = new LinkedHashSet<>(faits.couleurs() == null ? List.<String>of() : faits.couleurs());
        if (f.couleurs() != null) f.couleurs().stream().map(ExtracteurSite::hex).filter(c -> c != null).forEach(couleurs::add);

        List<Client.Offre> offres = new ArrayList<>();
        if (f.offres() != null) {
            for (var o : f.offres()) {
                if (o == null || vide(o.nom())) continue;
                offres.add(new Client.Offre(max(o.nom(), 150), max(o.description(), 500), max(o.prix(), 100)));
                if (offres.size() >= 8) break;
            }
        }
        List<String> cibles = new ArrayList<>();
        if (f.cibles() != null) {
            for (String c : f.cibles()) {
                if (!vide(c)) cibles.add(max(c, 100));
                if (cibles.size() >= 6) break;
            }
        }
        Integer annee = f.anneeCreation() != null && f.anneeCreation() >= 1800 && f.anneeCreation() <= Year.now().getValue() ? f.anneeCreation() : null;
        String langue = f.langue() != null && f.langue().toLowerCase(Locale.ROOT).matches("fr|en") ? f.langue().toLowerCase(Locale.ROOT) : null;

        return new FicheProposee(max(f.nom(), 150), max(f.secteur(), 150), max(f.description(), 4000), max(f.slogan(), 255),
                annee, max(vide(faits.ville()) ? f.ville() : faits.ville(), 100), max(vide(faits.pays()) ? f.pays() : faits.pays(), 100),
                max(f.zone(), 255), max(vide(faits.adresse()) ? f.adresse() : faits.adresse(), 255),
                max(vide(faits.horaires()) ? f.horaires() : faits.horaires(), 255),
                langue, max(f.ton(), 255), f.tutoiement(), offres, cibles,
                couleurs.stream().limit(3).toList(), faits.domaine(), faits.reseaux() == null ? Map.of() : faits.reseaux(), faits.whatsapp(),
                faits.logos() == null ? List.of() : faits.logos(), faits.photos() == null ? List.of() : faits.photos(),
                faits.sources() == null ? List.of() : faits.sources(), faits.avertissements() == null ? List.of() : faits.avertissements());
    }

    /** exemple.com : adresse sans protocole, sans « www. » ni chemin. */
    static String domaine(String url) {
        try {
            String h = URI.create(url).getHost();
            if (h == null) return null;
            h = h.toLowerCase(Locale.ROOT);
            return h.startsWith("www.") ? h.substring(4) : h;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean vide(String s) {
        return s == null || s.isBlank();
    }

    /** Texte nettoyé et plafonné ; null s'il est vide (la valeur est alors ignorée côté interface). */
    private static String max(String s, int taille) {
        if (vide(s)) return null;
        String propre = s.trim().replaceAll("[\\p{Cntrl}&&[^\\n]]", "");
        return propre.length() > taille ? propre.substring(0, taille) : propre;
    }
}
