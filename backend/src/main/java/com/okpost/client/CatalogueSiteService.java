package com.okpost.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.okpost.abonnement.AbonnementService;
import com.okpost.client.CatalogueImport.Produit;
import com.okpost.client.CatalogueImport.Resultat;
import com.okpost.client.ExtracteurSite.Page;
import com.okpost.commun.ApiException;
import com.okpost.ia.GenerationException;
import com.okpost.ia.Modeles.ProduitExtrait;
import com.okpost.ia.SelecteurIA;
import com.okpost.securite.Session;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Propose un catalogue à partir du site web du client. On essaie d'abord ce que les boutiques en ligne publient elles-mêmes
 * (Shopify, WooCommerce, données structurées des pages produit) : exact, sans IA. Sinon, l'IA lit le texte du site, avec un avertissement.
 * Rien n'est enregistré : l'utilisateur relit puis ajoute.
 */
@Service
public class CatalogueSiteService {

    private static final Logger log = LoggerFactory.getLogger(CatalogueSiteService.class);
    private static final int ANALYSES_PAR_HEURE = 12;
    private static final int PAGES_MAX = 14;
    private static final Duration BUDGET = Duration.ofSeconds(22);
    private static final int JSON_MAX = 3_000_000;
    private static final int TEXTE_IA_MAX = 24_000;

    private static final Pattern MOT_BOUTIQUE = Pattern.compile("(?i)boutique|shop|produit|product|catalogue|catalog|collection|store|article|nos-");
    private static final Pattern CHEMIN_PRODUIT = Pattern.compile("(?i)/(produits?|products?|p|articles?|items?)/[^/]+");
    private static final Pattern A_EVITER = Pattern.compile("(?i)panier|cart|compte|account|checkout|login|connexion|wishlist|\\.(jpe?g|png|webp|gif|pdf|zip)(\\?|$)");

    private final ChargeurSecurise http;
    private final LecteurSite lecteur;
    private final SelecteurIA selecteur;
    private final AbonnementService abonnements;
    private final ClientService clients;
    private final ObjectMapper json;
    private final Map<UUID, ArrayDeque<Instant>> appels = new ConcurrentHashMap<>();

    public CatalogueSiteService(ChargeurSecurise http, LecteurSite lecteur, SelecteurIA selecteur, AbonnementService abonnements,
                                ClientService clients, ObjectMapper json) {
        this.http = http;
        this.lecteur = lecteur;
        this.selecteur = selecteur;
        this.abonnements = abonnements;
        this.clients = clients;
        this.json = json;
    }

    public Resultat lire(Session s, UUID clientId, String url) {
        abonnements.exigerEcriture(s.workspaceId());
        Client client = clients.charger(s, clientId);
        limiter(s.workspaceId());
        URI depart = SecuriteUrl.normaliser(url);
        String origine = depart.getScheme() + "://" + depart.getHost() + (depart.getPort() > 0 ? ":" + depart.getPort() : "");
        List<String> avertissements = new ArrayList<>();

        List<Produit> produits = essayer("Shopify", () -> {
            var r = http.get(URI.create(origine + "/products.json?limit=250"), "application/json", JSON_MAX, Duration.ofSeconds(10));
            return r.ok() ? ParseursCatalogue.shopify(json, r.texte()) : List.of();
        });
        if (!produits.isEmpty()) {
            avertissements.add("Le site ne donne pas la devise des prix : les affiches utiliseront celle de la fiche (" + client.getDevise() + "). Vérifie les montants avant d'ajouter.");
            return termine(produits, "boutique Shopify", avertissements);
        }
        produits = essayer("WooCommerce", () -> {
            var r = http.get(URI.create(origine + "/wp-json/wc/store/v1/products?per_page=100"), "application/json", JSON_MAX, Duration.ofSeconds(10));
            return r.ok() ? ParseursCatalogue.woocommerce(json, r.texte()) : List.of();
        });
        if (!produits.isEmpty()) return termine(produits, "boutique WooCommerce", avertissements);

        produits = essayer("données structurées", () -> parcourirPages(depart));
        if (!produits.isEmpty()) {
            if (produits.size() < 3) avertissements.add("Seuls " + produits.size() + " produit(s) ont des données exploitables sur les pages lues : il peut en manquer.");
            return termine(produits, "pages produit du site", avertissements);
        }

        // Dernier recours : l'IA lit le texte du site
        produits = lireParIa(s, url);
        avertissements.add("Aucune donnée produit structurée sur ce site : les produits sont déduits du texte par l'IA, sans photo. Vérifie les noms et les prix.");
        return termine(produits, "texte du site (IA)", avertissements);
    }

    private interface Lecture {
        List<Produit> faire() throws Exception;
    }

    private static List<Produit> essayer(String nom, Lecture l) {
        try {
            return l.faire();
        } catch (ApiException e) {
            log.info("Catalogue {} non lisible : {}", nom, e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.info("Catalogue {} non lisible : {}", nom, e.toString());
            return List.of();
        }
    }

    private Resultat termine(List<Produit> bruts, String source, List<String> avertissements) {
        List<Produit> produits = CatalogueImport.nettoyer(bruts);
        if (produits.isEmpty()) throw ApiException.requeteInvalide("Aucun produit trouvé sur ce site.");
        long sansPhoto = produits.stream().filter(p -> p.imageUrl() == null).count();
        if (sansPhoto > 0 && sansPhoto < produits.size()) avertissements.add(sansPhoto + " produit(s) sans photo trouvée.");
        return new Resultat(produits, source, avertissements);
    }

    // ------------------------------------------------------------ pages avec données structurées

    private List<Produit> parcourirPages(URI depart) {
        long fin = System.nanoTime() + BUDGET.toNanos();
        Deque<URI> file = new ArrayDeque<>();
        Set<String> vues = new HashSet<>();
        List<Produit> trouves = new ArrayList<>();
        file.add(depart);
        int pages = 0;
        while (!file.isEmpty() && pages < PAGES_MAX && System.nanoTime() < fin) {
            URI uri = file.poll();
            if (!vues.add(uri.toString())) continue;
            Page page;
            try {
                page = lecteur.telecharger(uri);
            } catch (RuntimeException e) {
                continue;
            }
            pages++;
            URI base = URI.create(page.url());
            trouves.addAll(ParseursCatalogue.jsonLd(json, page.doc(), base));
            if (trouves.size() >= CatalogueImport.PRODUITS_MAX) break;
            for (URI lien : liensCatalogue(page.doc(), base, PAGES_MAX - pages)) {
                if (!vues.contains(lien.toString())) file.add(lien);
            }
        }
        return trouves;
    }

    /** Pages de boutique d'abord, puis pages produit : adresses du même site, sans panier ni compte client. */
    static List<URI> liensCatalogue(Document doc, URI base, int max) {
        List<URI> listes = new ArrayList<>();
        List<URI> produits = new ArrayList<>();
        String hote = sansWww(base.getHost());
        for (Element a : doc.select("a[href]")) {
            URI u;
            try {
                u = URI.create(a.absUrl("href").replaceAll("#.*$", ""));
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (u.getHost() == null || !sansWww(u.getHost()).equals(hote) || !"http".equals(u.getScheme()) && !"https".equals(u.getScheme())) continue;
            String texte = u.getPath() + " " + a.text();
            if (A_EVITER.matcher(u.toString()).find()) continue;
            if (CHEMIN_PRODUIT.matcher(u.getPath() == null ? "" : u.getPath()).find()) {
                if (!produits.contains(u)) produits.add(u);
            } else if (MOT_BOUTIQUE.matcher(texte).find() && !listes.contains(u) && u.getPath() != null && u.getPath().length() > 1) {
                listes.add(u);
            }
        }
        List<URI> resultat = new ArrayList<>(listes.subList(0, Math.min(listes.size(), 4)));
        for (URI p : produits) {
            if (resultat.size() >= Math.max(0, max)) break;
            resultat.add(p);
        }
        return resultat.size() > max ? resultat.subList(0, Math.max(0, max)) : resultat;
    }

    private static String sansWww(String hote) {
        String h = hote == null ? "" : hote.toLowerCase(Locale.ROOT);
        return h.startsWith("www.") ? h.substring(4) : h;
    }

    // ------------------------------------------------------------ dernier recours : l'IA

    private List<Produit> lireParIa(Session s, String url) {
        var site = lecteur.lire(url);
        String contenu = "[1] " + site.pourIa();
        if (contenu.length() > TEXTE_IA_MAX) contenu = contenu.substring(0, TEXTE_IA_MAX);
        try {
            var extraits = selecteur.pour(s.workspaceId()).extraireProduits(s.workspaceId(), contenu);
            List<Produit> produits = new ArrayList<>();
            if (extraits != null && extraits.produits() != null) {
                for (ProduitExtrait p : extraits.produits()) {
                    produits.add(new Produit(p.nom(), p.description(), p.prix(), p.categorie(), p.tailles(), p.couleurs(), null, null));
                }
            }
            return produits;
        } catch (GenerationException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    private void limiter(UUID workspaceId) {
        ArrayDeque<Instant> file = appels.computeIfAbsent(workspaceId, k -> new ArrayDeque<>());
        synchronized (file) {
            Instant limite = Instant.now().minus(Duration.ofHours(1));
            while (!file.isEmpty() && file.peekFirst().isBefore(limite)) file.pollFirst();
            if (file.size() >= ANALYSES_PAR_HEURE) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Trop de lectures de catalogue : réessaie dans quelques minutes");
            }
            file.addLast(Instant.now());
        }
    }
}
