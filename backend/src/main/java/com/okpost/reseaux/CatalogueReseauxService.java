package com.okpost.reseaux;

import com.okpost.abonnement.AbonnementService;
import com.okpost.client.CatalogueImport;
import com.okpost.client.CatalogueImport.Produit;
import com.okpost.client.CatalogueImport.Resultat;
import com.okpost.client.ClientService;
import com.okpost.commun.ApiException;
import com.okpost.ia.GenerationException;
import com.okpost.ia.Modeles.ProduitExtrait;
import com.okpost.ia.SelecteurIA;
import com.okpost.securite.Session;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Propose un catalogue à partir des publications de la Page Facebook et du compte Instagram reliés : l'IA repère les produits cités
 * (nom, prix, tailles, couleurs) et on leur associe la photo de la publication. Rien n'est enregistré : l'utilisateur relit puis ajoute.
 */
@Service
public class CatalogueReseauxService {

    private static final int PUBLICATIONS_MAX = 50;
    private static final int MESSAGE_MAX = 700;
    private static final int CONTENU_MAX = 24_000;
    private static final int ANALYSES_PAR_HEURE = 12;

    /** Une publication lue : son texte et la photo qui l'accompagne. */
    record Element(String texte, String imageUrl) {}

    private final ClientService clients;
    private final CompteSocialRepository comptes;
    private final Chiffreur chiffreur;
    private final MetaApi meta;
    private final SelecteurIA selecteur;
    private final AbonnementService abonnements;
    private final Map<UUID, ArrayDeque<Instant>> appels = new ConcurrentHashMap<>();

    public CatalogueReseauxService(ClientService clients, CompteSocialRepository comptes, Chiffreur chiffreur, MetaApi meta,
                                   SelecteurIA selecteur, AbonnementService abonnements) {
        this.clients = clients;
        this.comptes = comptes;
        this.chiffreur = chiffreur;
        this.meta = meta;
        this.selecteur = selecteur;
        this.abonnements = abonnements;
    }

    public Resultat proposer(Session s, UUID clientId) {
        abonnements.exigerEcriture(s.workspaceId());
        clients.charger(s, clientId);
        List<CompteSocial> fb = comptes.findByClientIdAndReseau(clientId, ComptesSociauxService.FACEBOOK).stream().filter(CompteSocial::isActif).toList();
        List<CompteSocial> ig = comptes.findByClientIdAndReseau(clientId, ComptesSociauxService.INSTAGRAM).stream().filter(CompteSocial::isActif).toList();
        if (fb.isEmpty() && ig.isEmpty()) {
            throw ApiException.requeteInvalide("Aucune page reliée : relie d'abord la Page Facebook ou le compte Instagram du client (rubrique « Réseaux connectés »).");
        }
        limiter(s.workspaceId());

        List<Element> elements = new ArrayList<>();
        List<String> avertissements = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        if (!fb.isEmpty()) {
            try {
                CompteSocial c = fb.get(0);
                List<MetaApi.PublicationPhoto> l = meta.publicationsAvecPhoto(c.getCompteExterneId(), chiffreur.dechiffrer(c.getJetonChiffre()), PUBLICATIONS_MAX);
                l.forEach(p -> elements.add(new Element(p.message(), p.imageUrl())));
                if (!l.isEmpty()) sources.add("Facebook");
            } catch (RuntimeException e) {
                avertissements.add("Page Facebook illisible (" + e.getMessage() + ") : reconnecte la Page si son accès a expiré.");
            }
        }
        if (!ig.isEmpty()) {
            try {
                CompteSocial c = ig.get(0);
                var l = meta.mediasInstagram(c.getCompteExterneId(), chiffreur.dechiffrer(c.getJetonChiffre()), PUBLICATIONS_MAX);
                l.stream().filter(m -> m.legende() != null && !m.legende().isBlank()).forEach(m -> elements.add(new Element(m.legende(), m.url())));
                if (l.stream().anyMatch(m -> m.legende() != null && !m.legende().isBlank())) sources.add("Instagram");
            } catch (RuntimeException e) {
                avertissements.add("Compte Instagram illisible (" + e.getMessage() + ").");
            }
        }
        if (elements.isEmpty()) {
            throw ApiException.requeteInvalide("Aucune publication avec du texte à analyser" + (avertissements.isEmpty() ? "." : " : " + String.join(" ", avertissements)));
        }

        try {
            var extraits = selecteur.pour(s.workspaceId()).extraireProduits(s.workspaceId(), contenu(elements));
            List<Produit> produits = new ArrayList<>();
            if (extraits != null && extraits.produits() != null) {
                for (ProduitExtrait p : extraits.produits()) produits.add(vers(p, elements));
            }
            List<Produit> nets = CatalogueImport.nettoyer(produits);
            if (nets.isEmpty()) throw ApiException.requeteInvalide("L'IA n'a trouvé aucun produit identifiable dans les dernières publications.");
            avertissements.add("Produits déduits des publications par l'IA : vérifie les noms, les prix, les tailles et les couleurs avant d'ajouter.");
            if (nets.stream().anyMatch(p -> p.prix() == null)) avertissements.add("Certains produits n'ont pas de prix : aucune publication n'en donnait.");
            return new Resultat(nets, String.join(" et ", sources), avertissements);
        } catch (GenerationException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    /** Blocs numérotés [n] : l'IA renvoie le numéro d'où vient chaque produit, ce qui permet de retrouver sa photo. */
    static String contenu(List<Element> elements) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < elements.size(); i++) {
            String t = elements.get(i).texte().trim().replaceAll("\\s+", " ");
            if (t.length() > MESSAGE_MAX) t = t.substring(0, MESSAGE_MAX);
            String bloc = "[" + (i + 1) + "] " + t + "\n\n";
            if (sb.length() + bloc.length() > CONTENU_MAX) break;
            sb.append(bloc);
        }
        return sb.toString();
    }

    static Produit vers(ProduitExtrait p, List<Element> elements) {
        String image = null;
        if (p.source() != null && p.source() >= 1 && p.source() <= elements.size()) image = elements.get(p.source() - 1).imageUrl();
        return new Produit(p.nom(), p.description(), p.prix(), p.categorie(), p.tailles(), p.couleurs(), null, image);
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
