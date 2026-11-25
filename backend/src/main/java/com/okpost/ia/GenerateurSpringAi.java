package com.okpost.ia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.okpost.ia.Modeles.DemandeCalendrier;
import com.okpost.ia.Modeles.DetailGenere;
import com.okpost.ia.Modeles.GrilleGeneree;
import com.okpost.ia.Modeles.IdeeADetailler;
import com.okpost.ia.Modeles.IdeeGeneree;
import com.okpost.ia.Modeles.LotDetails;
import com.okpost.ia.Modeles.TexteRegenere;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implémentation commune aux fournisseurs, basée sur le ChatModel de Spring AI.
 * Gemini (via son point d'accès compatible OpenAI) et Claude n'en diffèrent que par le modèle et les options.
 */
public class GenerateurSpringAi implements GenerateurIA {

    private static final Logger log = LoggerFactory.getLogger(GenerateurSpringAi.class);
    static final Set<String> FORMATS = Set.of("IMAGE", "CARROUSEL", "VIDEO", "TEXTE", "STORY");
    static final Set<String> OBJECTIFS = Set.of("NOTORIETE", "ENGAGEMENT", "VENTE", "CONFIANCE", "RECRUTEMENT");

    private final String nom;
    private final ChatModel modele;
    private final boolean configure;
    private final String modeleStrategie;
    private final String modeleRedaction;
    private final Function<String, ChatOptions> options;
    private final Prompts prompts;
    private final FileAttenteIA file;
    private final Journal journal;
    private final ObjectMapper json;

    public GenerateurSpringAi(String nom, ChatModel modele, boolean configure, String modeleStrategie,
                              String modeleRedaction, Function<String, ChatOptions> options, Prompts prompts,
                              FileAttenteIA file, Journal journal, ObjectMapper json) {
        this.nom = nom;
        this.modele = modele;
        this.configure = configure;
        this.modeleStrategie = modeleStrategie;
        this.modeleRedaction = modeleRedaction;
        this.options = options;
        this.prompts = prompts;
        this.file = file;
        this.journal = journal;
        this.json = json;
    }

    @Override
    public String nom() { return nom; }

    @Override
    public boolean configure() { return configure; }

    @Override
    public String modele() { return modeleStrategie; }

    @Override
    public GrilleGeneree genererCalendrier(UUID ws, String fiche, DemandeCalendrier d) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("debut", d.debut().toString());
        v.put("fin", d.fin().toString());
        Map<String, Integer> attendus = d.attendus();
        v.put("attendu", attendus.values().stream().mapToInt(Integer::intValue).sum() + " au total, soit "
                + attendus.entrySet().stream().map(e -> e.getKey() + " : " + e.getValue()).collect(Collectors.joining(", ")));
        v.put("piliers", d.piliers() == null || d.piliers().isEmpty()
                ? "à proposer (3 à 5 piliers)" : String.join(", ", d.piliers()));
        v.put("deja", d.dejaPrevues() == null || d.dejaPrevues().isEmpty()
                ? "aucune" : d.dejaPrevues().stream().map(x -> "- " + x).collect(Collectors.joining("\n")));
        v.put("reseaux", String.join(", ", d.reseaux()));
        v.put("objectifs", vide(d.objectifs()));
        v.put("tempsForts", vide(d.tempsForts()));
        v.put("langue", langue(d.langue()));
        String user = prompts.rendre("calendrier", v);
        return appelerJson(ws, "calendrier", modeleStrategie, systeme(fiche), user, GrilleGeneree.class,
                g -> validerGrille(g, d));
    }

    @Override
    public List<DetailGenere> genererDetails(UUID ws, String fiche, String langue, List<IdeeADetailler> idees) {
        String listeIdees;
        try {
            listeIdees = json.writerWithDefaultPrettyPrinter().writeValueAsString(idees);
        } catch (Exception e) {
            throw new GenerationException("Sérialisation des idées impossible", e);
        }
        String user = prompts.rendre("details", Map.of("langue", langue(langue), "idees", listeIdees));
        LotDetails lot = appelerJson(ws, "details", modeleRedaction, systeme(fiche), user, LotDetails.class,
                l -> validerDetails(l, idees));
        return lot.publications();
    }

    @Override
    public String regenererChamp(UUID ws, String fiche, String langue, String publication, String champ, String consigne) {
        String user = prompts.rendre("regenerer", Map.of("publication", publication, "champ", champ,
                "consigne", vide(consigne), "langue", langue(langue)));
        TexteRegenere t = appelerJson(ws, "regenerer:" + champ, modeleRedaction, systeme(fiche), user, TexteRegenere.class,
                r -> r.texte() == null || r.texte().isBlank() ? List.of("texte vide") : List.of());
        return t.texte().trim();
    }

    @Override
    public String reformulerTexte(UUID ws, String fiche, String langue, String texte, String sujet, String instruction, int longueurMax) {
        String user = prompts.rendre("reformuler", Map.of("texte", texte, "sujet", vide(sujet), "instruction", instruction,
                "langue", langue(langue), "max", String.valueOf(longueurMax)));
        TexteRegenere t = appelerJson(ws, "reformuler", modeleRedaction, prompts.rendre("reformuler-systeme", Map.of("fiche", fiche)), user,
                TexteRegenere.class, r -> r.texte() == null || r.texte().isBlank() ? List.of("texte vide") : List.of());
        return t.texte().trim();
    }

    @Override
    public Modeles.FicheSite analyserSite(UUID ws, String contenuSite) {
        String user = prompts.rendre("site", Map.of("contenu", contenuSite));
        return appelerJson(ws, "site", modeleRedaction, prompts.rendre("site-systeme", Map.of()), user, Modeles.FicheSite.class,
                r -> List.of());
    }

    @Override
    public Modeles.Recommandations recommandations(UUID ws, String fiche, String bilan) {
        String user = prompts.rendre("bilan", Map.of("bilan", bilan));
        return appelerJson(ws, "bilan", modeleStrategie, systeme(fiche), user, Modeles.Recommandations.class,
                r -> r.recommandations() == null || r.recommandations().isEmpty() ? List.of("aucune recommandation") : List.of());
    }

    @Override
    public List<Modeles.TempsFortSuggere> suggererTempsForts(UUID ws, String fiche, java.time.LocalDate debut,
                                                              java.time.LocalDate fin, List<String> deja) {
        String user = prompts.rendre("temps-forts", Map.of("debut", debut.toString(), "fin", fin.toString(),
                "deja", deja == null || deja.isEmpty() ? "aucun" : String.join(", ", deja)));
        Modeles.TempsFortsSuggeres r = appelerJson(ws, "temps-forts", modeleStrategie, systeme(fiche), user,
                Modeles.TempsFortsSuggeres.class,
                t -> t.suggestions() == null ? List.of("liste absente") : List.of());
        return r.suggestions().stream()
                .filter(x -> x.nom() != null && !x.nom().isBlank())
                .map(x -> x.date() != null && (x.date().isBefore(debut) || x.date().isAfter(fin))
                        ? new Modeles.TempsFortSuggere(x.nom(), null, x.angle()) : x)
                .limit(8).toList();
    }

    // ------------------------------------------------------------------

    private String systeme(String fiche) {
        return prompts.rendre("systeme", Map.of("fiche", fiche));
    }

    /** Appelle le modèle, convertit le JSON, valide, et relance une fois avec les erreurs si besoin. */
    private <T> T appelerJson(UUID ws, String operation, String nomModele, String systeme, String user,
                              Class<T> type, Function<T, List<String>> valider) {
        BeanOutputConverter<T> convertisseur = new BeanOutputConverter<>(type);
        String demande = user + "\n\n" + convertisseur.getFormat();
        List<String> erreurs = List.of();
        for (int essai = 1; essai <= 2; essai++) {
            String message = essai == 1 ? demande
                    : demande + "\n\nTa réponse précédente était invalide : " + String.join(" ; ", erreurs)
                      + ". Corrige ces points et renvoie le JSON complet.";
            String texte = appeler(ws, operation, nomModele, systeme, message);
            T resultat;
            try {
                resultat = convertisseur.convert(nettoyer(texte));
            } catch (RuntimeException e) {
                erreurs = List.of("JSON illisible (" + e.getClass().getSimpleName() + ")");
                log.warn("Réponse {} illisible (essai {}) : {}", operation, essai, e.getMessage());
                continue;
            }
            erreurs = resultat == null ? List.of("réponse vide") : valider.apply(resultat);
            if (erreurs.isEmpty()) {
                return resultat;
            }
            log.warn("Réponse {} invalide (essai {}) : {}", operation, essai, erreurs);
        }
        throw new GenerationException("La génération a échoué : " + String.join(" ; ", erreurs));
    }

    private String appeler(UUID ws, String operation, String nomModele, String systeme, String user) {
        if (!configure) {
            throw new GenerationException("Le fournisseur IA « " + nom + " » n'est pas configuré (clé API absente)");
        }
        log.info("IA {} / {} : {} — envoi de la demande", nom, nomModele, operation);
        return file.executer(() -> {
            long debut = System.currentTimeMillis();
            try {
                List<Message> messages = List.of(new SystemMessage(systeme), new UserMessage(user));
                ChatResponse r = modele.call(new Prompt(messages, options.apply(nomModele)));
                Usage u = r.getMetadata() == null ? null : r.getMetadata().getUsage();
                journal.noter(ws, nom, nomModele, operation,
                        u == null ? null : u.getPromptTokens(), u == null ? null : u.getCompletionTokens(),
                        System.currentTimeMillis() - debut, true, null);
                log.info("IA {} / {} : {} — réponse en {} s ({} jetons reçus)", nom, nomModele, operation,
                        (System.currentTimeMillis() - debut) / 1000, u == null ? "?" : u.getCompletionTokens());
                if (r.getResult() == null || r.getResult().getOutput() == null) {
                    throw new GenerationException("Réponse vide du fournisseur IA");
                }
                return r.getResult().getOutput().getText();
            } catch (RuntimeException e) {
                log.warn("IA {} / {} : {} — échec après {} s : {}", nom, nomModele, operation,
                        (System.currentTimeMillis() - debut) / 1000, e.getMessage());
                journal.noter(ws, nom, nomModele, operation, null, null,
                        System.currentTimeMillis() - debut, false, e.getMessage());
                throw e;
            }
        });
    }

    /** Retire les éventuelles balises ```json autour de la réponse. */
    static String nettoyer(String texte) {
        if (texte == null) return null;
        String t = texte.trim();
        if (t.startsWith("```")) {
            int debut = t.indexOf('\n');
            int fin = t.lastIndexOf("```");
            if (debut > 0 && fin > debut) {
                t = t.substring(debut + 1, fin).trim();
            }
        }
        return t;
    }

    static List<String> validerGrille(GrilleGeneree g, DemandeCalendrier d) {
        List<String> erreurs = new ArrayList<>();
        if (g.publications() == null || g.publications().isEmpty()) {
            return List.of("aucune publication");
        }
        Set<String> reseaux = d.reseaux().stream().map(r -> r.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        int i = 0;
        for (IdeeGeneree p : g.publications()) {
            i++;
            if (p.date() == null || p.date().isBefore(d.debut()) || p.date().isAfter(d.fin())) {
                erreurs.add("publication " + i + " : date hors période");
            }
            if (p.reseau() == null || !reseaux.contains(p.reseau().toUpperCase(Locale.ROOT))) {
                erreurs.add("publication " + i + " : réseau non demandé (" + p.reseau() + ")");
            }
            if (p.format() == null || !FORMATS.contains(p.format().toUpperCase(Locale.ROOT))) {
                erreurs.add("publication " + i + " : format invalide (" + p.format() + ")");
            }
            if (p.idee() == null || p.idee().isBlank()) {
                erreurs.add("publication " + i + " : idée vide");
            }
            if (erreurs.size() > 8) break;
        }
        // Nombre de publications par réseau : tolérance d'une publication en moins.
        Map<String, Long> parReseau = g.publications().stream().filter(p -> p.reseau() != null)
                .collect(Collectors.groupingBy(p -> p.reseau().toUpperCase(Locale.ROOT), Collectors.counting()));
        d.attendus().forEach((reseau, n) -> {
            long obtenu = parReseau.getOrDefault(reseau, 0L);
            if (obtenu < n - 1) {
                erreurs.add(reseau + " : " + obtenu + " publication(s) au lieu de " + n
                        + " — couvre toute la période jusqu'au " + d.fin());
            }
        });
        return erreurs;
    }

    static List<String> validerDetails(LotDetails lot, List<IdeeADetailler> idees) {
        if (lot.publications() == null) {
            return List.of("liste de publications absente");
        }
        List<String> erreurs = new ArrayList<>();
        Map<Integer, DetailGenere> parIndex = lot.publications().stream()
                .collect(Collectors.toMap(DetailGenere::index, x -> x, (a, b) -> a));
        for (IdeeADetailler idee : idees) {
            DetailGenere dg = parIndex.get(idee.index());
            if (dg == null) {
                erreurs.add("index " + idee.index() + " manquant");
                continue;
            }
            if (dg.legende() == null || dg.legende().isBlank()) {
                erreurs.add("index " + idee.index() + " : légende vide");
            }
            if ("VIDEO".equals(idee.format())) {
                if (dg.scenario() == null || dg.scenario().plans() == null || dg.scenario().plans().isEmpty()) {
                    erreurs.add("index " + idee.index() + " : scénario vidéo manquant");
                }
            } else if (dg.briefVisuel() == null || dg.briefVisuel().promptImage() == null) {
                erreurs.add("index " + idee.index() + " : brief visuel manquant");
            }
        }
        return erreurs;
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? "aucun précisé" : s;
    }

    private static String langue(String code) {
        return "en".equalsIgnoreCase(code) ? "anglais" : "français";
    }

    static final DateTimeFormatter JOUR = DateTimeFormatter.ISO_LOCAL_DATE;
}
