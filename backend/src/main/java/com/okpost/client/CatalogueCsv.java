package com.okpost.client;

import com.okpost.commun.ApiException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lecture d'un catalogue de produits au format CSV (export de tableur : Excel, Google Sheets, LibreOffice).
 * Séparateur détecté (; , ou tabulation), guillemets et sauts de ligne internes gérés, accents des en-têtes ignorés.
 */
final class CatalogueCsv {

    private CatalogueCsv() {}

    static final int LIGNES_MAX = 300;
    static final int TAILLES_MAX = 20;

    record Ligne(String nom, String prix, String description, String categorie, List<String> tailles, List<String> couleurs,
                 List<String> photos, Boolean disponible) {}

    record Lecture(List<Ligne> lignes, List<String> avertissements) {}

    private static final Map<String, String> ALIAS = alias();

    private static Map<String, String> alias() {
        Map<String, String> m = new HashMap<>();
        for (String s : new String[]{"nom", "produit", "article", "name", "designation", "libelle", "titre"}) m.put(s, "nom");
        for (String s : new String[]{"prix", "price", "tarif", "montant", "cout"}) m.put(s, "prix");
        for (String s : new String[]{"description", "desc", "details", "detail"}) m.put(s, "description");
        for (String s : new String[]{"categorie", "category", "famille", "rayon", "type", "gamme"}) m.put(s, "categorie");
        for (String s : new String[]{"tailles", "taille", "size", "sizes", "pointure", "pointures"}) m.put(s, "tailles");
        for (String s : new String[]{"couleurs", "couleur", "color", "colors", "colour", "colours", "coloris"}) m.put(s, "couleurs");
        for (String s : new String[]{"photo", "photos", "image", "images", "fichier", "fichiers", "visuel", "visuels"}) m.put(s, "photos");
        for (String s : new String[]{"stock", "disponible", "disponibilite", "dispo", "availability", "available", "enstock"}) m.put(s, "disponible");
        return m;
    }

    static Lecture lire(byte[] contenu) {
        String texte = decoder(contenu);
        if (texte.isBlank()) throw ApiException.requeteInvalide("Le fichier est vide");
        char sep = separateur(texte);
        List<List<String>> lignes = decouper(texte, sep);
        if (lignes.size() < 2) throw ApiException.requeteInvalide("Le fichier doit contenir une ligne d'en-têtes puis au moins un produit");

        List<String> entetes = lignes.get(0);
        Map<String, Integer> colonnes = new HashMap<>();
        List<String> avertissements = new ArrayList<>();
        for (int i = 0; i < entetes.size(); i++) {
            String cle = ALIAS.get(normaliser(entetes.get(i)));
            if (cle != null) colonnes.putIfAbsent(cle, i);
            else if (!entetes.get(i).isBlank()) avertissements.add("Colonne « " + entetes.get(i).trim() + " » ignorée (non reconnue)");
        }
        if (!colonnes.containsKey("nom")) {
            throw ApiException.requeteInvalide("Colonne « nom » introuvable. Les colonnes attendues : nom, prix, description, categorie, tailles, couleurs, photo, stock.");
        }

        List<Ligne> resultat = new ArrayList<>();
        int ignorees = 0;
        for (int n = 1; n < lignes.size(); n++) {
            List<String> l = lignes.get(n);
            String nom = champ(l, colonnes.get("nom"));
            if (nom == null) {
                if (l.stream().anyMatch(c -> !c.isBlank())) ignorees++;
                continue;
            }
            if (resultat.size() >= LIGNES_MAX) {
                avertissements.add("Seuls les " + LIGNES_MAX + " premiers produits sont lus : découpe le fichier pour importer la suite.");
                break;
            }
            resultat.add(new Ligne(max(nom, 150), max(champ(l, colonnes.get("prix")), 100), max(champ(l, colonnes.get("description")), 500),
                    max(champ(l, colonnes.get("categorie")), 60), liste(champ(l, colonnes.get("tailles"))),
                    liste(champ(l, colonnes.get("couleurs"))), listeBrute(champ(l, colonnes.get("photos"))),
                    disponible(champ(l, colonnes.get("disponible")))));
        }
        if (ignorees > 0) avertissements.add(ignorees + " ligne(s) sans nom de produit ignorée(s)");
        if (resultat.isEmpty()) throw ApiException.requeteInvalide("Aucun produit trouvé dans le fichier");
        return new Lecture(resultat, avertissements);
    }

    // ------------------------------------------------------------ décodage et découpage

    /** UTF-8 si possible (avec ou sans BOM), sinon Windows-1252 : l'encodage des CSV exportés par Excel en français. */
    static String decoder(byte[] contenu) {
        int debut = contenu.length >= 3 && (contenu[0] & 0xFF) == 0xEF && (contenu[1] & 0xFF) == 0xBB && (contenu[2] & 0xFF) == 0xBF ? 3 : 0;
        ByteBuffer tampon = ByteBuffer.wrap(contenu, debut, contenu.length - debut);
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(tampon).toString();
        } catch (CharacterCodingException e) {
            return new String(contenu, debut, contenu.length - debut, Charset.forName("windows-1252"));
        }
    }

    static char separateur(String texte) {
        int fin = texte.indexOf('\n');
        String premiere = fin < 0 ? texte : texte.substring(0, fin);
        long pointVirgules = premiere.chars().filter(c -> c == ';').count();
        long virgules = premiere.chars().filter(c -> c == ',').count();
        long tabulations = premiere.chars().filter(c -> c == '\t').count();
        if (tabulations > pointVirgules && tabulations > virgules) return '\t';
        return pointVirgules >= virgules && pointVirgules > 0 ? ';' : ',';
    }

    static List<List<String>> decouper(String texte, char sep) {
        List<List<String>> lignes = new ArrayList<>();
        List<String> courante = new ArrayList<>();
        StringBuilder champ = new StringBuilder();
        boolean guillemets = false;
        for (int i = 0; i < texte.length(); i++) {
            char c = texte.charAt(i);
            if (guillemets) {
                if (c == '"') {
                    if (i + 1 < texte.length() && texte.charAt(i + 1) == '"') { champ.append('"'); i++; }
                    else guillemets = false;
                } else {
                    champ.append(c);
                }
            } else if (c == '"' && champ.isEmpty()) {
                guillemets = true;
            } else if (c == sep) {
                courante.add(champ.toString());
                champ.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < texte.length() && texte.charAt(i + 1) == '\n') i++;
                courante.add(champ.toString());
                champ.setLength(0);
                if (courante.stream().anyMatch(x -> !x.isBlank())) lignes.add(courante);
                courante = new ArrayList<>();
            } else {
                champ.append(c);
            }
        }
        courante.add(champ.toString());
        if (courante.stream().anyMatch(x -> !x.isBlank())) lignes.add(courante);
        return lignes;
    }

    // ------------------------------------------------------------ valeurs

    static String normaliser(String entete) {
        return Normalizer.normalize(entete == null ? "" : entete, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private static String champ(List<String> ligne, Integer colonne) {
        if (colonne == null || colonne >= ligne.size()) return null;
        String v = ligne.get(colonne).trim().replaceAll("[\\p{Cntrl}&&[^\\n]]", "");
        return v.isEmpty() ? null : v;
    }

    private static String max(String s, int taille) {
        if (s == null) return null;
        return s.length() > taille ? s.substring(0, taille) : s;
    }

    /** Tailles ou couleurs séparées par des virgules, points-virgules ou barres verticales. */
    static List<String> liste(String valeur) {
        List<String> l = new ArrayList<>();
        for (String v : listeBrute(valeur)) {
            String t = v.length() > 20 ? v.substring(0, 20) : v;
            if (!l.contains(t)) l.add(t);
            if (l.size() >= TAILLES_MAX) break;
        }
        return l;
    }

    static List<String> listeBrute(String valeur) {
        List<String> l = new ArrayList<>();
        if (valeur == null) return l;
        for (String v : valeur.split("[;,|\\n]")) {
            String t = v.trim();
            if (!t.isEmpty()) l.add(t);
        }
        return l;
    }

    /** « non », « 0 », « rupture », « épuisé » : indisponible ; vide : on ne dit rien. */
    static Boolean disponible(String valeur) {
        if (valeur == null || valeur.isBlank()) return null;
        // Une quantité en stock : 0 = rupture, toute autre valeur = disponible
        if (valeur.trim().matches("\\d+")) return Integer.parseInt(valeur.trim().substring(0, Math.min(9, valeur.trim().length()))) > 0;
        String v = normaliser(valeur);
        for (String non : new String[]{"non", "no", "rupture", "epuise", "indisponible", "horsstock", "false", "faux", "out"}) {
            if (v.startsWith(non)) return false;
        }
        return true;
    }
}
