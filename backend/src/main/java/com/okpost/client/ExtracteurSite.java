package com.okpost.client;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tire d'un site web ce qui sert à remplir une fiche client : identité, couleurs, réseaux, coordonnées et texte utile. */
final class ExtracteurSite {

    record Page(String url, Document doc) {}

    record ContenuSite(String url, String titre, String description, String nomSite, List<String> couleurs,
                       Map<String, String> reseaux, String whatsapp, List<String> telephones, List<String> logos,
                       List<String> photos, String donneesStructurees, String texte, List<String> pagesLues) {

        /** Texte envoyé à l'IA. Le contenu du site est une donnée à analyser, jamais une consigne. */
        String pourIa() {
            StringBuilder sb = new StringBuilder();
            sb.append("URL du site : ").append(url).append('\n');
            if (!titre.isBlank()) sb.append("Titre : ").append(titre).append('\n');
            if (!description.isBlank()) sb.append("Description : ").append(description).append('\n');
            if (!nomSite.isBlank()) sb.append("Nom du site : ").append(nomSite).append('\n');
            if (!couleurs.isEmpty()) sb.append("Couleurs déclarées par le site : ").append(String.join(", ", couleurs)).append('\n');
            if (!reseaux.isEmpty()) sb.append("Réseaux sociaux trouvés : ").append(reseaux).append('\n');
            if (!telephones.isEmpty()) sb.append("Téléphones : ").append(String.join(", ", telephones)).append('\n');
            if (whatsapp != null) sb.append("WhatsApp : ").append(whatsapp).append('\n');
            if (!donneesStructurees.isBlank()) sb.append("\nDonnées structurées (JSON-LD) :\n").append(donneesStructurees).append('\n');
            sb.append("\nTexte des pages :\n").append(texte);
            return sb.toString();
        }
    }

    static final int TEXTE_PAR_PAGE = 5000;
    static final int TEXTE_TOTAL = 14000;

    private static final Pattern HEX = Pattern.compile("^#?([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$");
    private static final Pattern EXTENSION_FICHIER = Pattern.compile("(?i)\\.(pdf|jpe?g|png|gif|webp|svg|zip|docx?|xlsx?|pptx?|mp[34]|avi|mov)(\\?.*)?$");
    private static final List<Pattern> CATEGORIES = List.of(
            Pattern.compile("(?i)(service|offre|produit|tarif|prix|menu|boutique|shop|catalogue|prestation|activit|formation|solution)"),
            Pattern.compile("(?i)(a-propos|apropos|à propos|about|qui-sommes|qui sommes|notre-histoire|histoire|equipe|équipe|team|presentation|présentation)"),
            Pattern.compile("(?i)(contact|nous-joindre|nous joindre|nous-trouver|adresse|localisation|horaire|acces|accès)"));

    private ExtracteurSite() {}

    // ------------------------------------------------------------------ pages à lire en plus de l'accueil

    /** Jusqu'à 3 pages du même site : offres/services, présentation, contact. */
    static List<String> pagesUtiles(Document accueil, URI base) {
        String hote = sansWww(base.getHost());
        List<String> choisies = new ArrayList<>();
        for (Pattern categorie : CATEGORIES) {
            for (Element a : accueil.select("a[href]")) {
                String lien = a.absUrl("href");
                if (lien.isBlank() || !lien.toLowerCase(Locale.ROOT).startsWith("http")) continue;
                URI uri;
                try {
                    uri = URI.create(lien.replaceAll("#.*$", ""));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                if (uri.getHost() == null || !sansWww(uri.getHost()).equals(hote)) continue;
                if (EXTENSION_FICHIER.matcher(lien).find()) continue;
                String chemin = uri.getPath() == null ? "" : uri.getPath();
                if (chemin.isEmpty() || chemin.equals("/")) continue;
                if (!categorie.matcher(chemin + " " + a.text()).find()) continue;
                String propre = lien.replaceAll("#.*$", "");
                if (!choisies.contains(propre) && !propre.equals(base.toString())) {
                    choisies.add(propre);
                    break;
                }
            }
        }
        return choisies;
    }

    // ------------------------------------------------------------------ extraction

    static ContenuSite extraire(List<Page> pages) {
        Document accueil = pages.get(0).doc();
        String url = pages.get(0).url();

        String description = meta(accueil, "meta[name=description]");
        if (description.isBlank()) description = meta(accueil, "meta[property=og:description]");

        Set<String> couleurs = new LinkedHashSet<>();
        for (String selecteur : List.of("meta[name=theme-color]", "meta[name=msapplication-TileColor]")) {
            String c = hex(meta(accueil, selecteur));
            if (c != null) couleurs.add(c);
        }

        Map<String, String> reseaux = new LinkedHashMap<>();
        String whatsapp = null;
        Set<String> telephones = new LinkedHashSet<>();
        Set<String> logos = new LinkedHashSet<>();
        Set<String> photos = new LinkedHashSet<>();
        StringBuilder json = new StringBuilder();
        StringBuilder texte = new StringBuilder();
        List<String> lues = new ArrayList<>();

        for (Page p : pages) {
            lues.add(p.url());
            Document d = p.doc();
            for (Element a : d.select("a[href]")) {
                String href = a.absUrl("href");
                String brut = a.attr("href");
                if (brut.toLowerCase(Locale.ROOT).startsWith("tel:") && telephones.size() < 3) {
                    String tel = brut.substring(4).replaceAll("[^+0-9 ]", "").trim();
                    if (tel.replaceAll("\\D", "").length() >= 7) telephones.add(tel);
                }
                String wa = whatsapp(href);
                if (wa != null && whatsapp == null) whatsapp = wa;
                String reseau = reseau(href);
                if (reseau != null) reseaux.putIfAbsent(reseau, href.replaceAll("[?#].*$", "").replaceAll("/+$", ""));
            }
            if (p == pages.get(0)) {
                for (Element l : d.select("link[rel~=(?i)(apple-touch-icon|icon)]")) ajouterLogo(logos, l.absUrl("href"));
                ajouterLogo(logos, d.select("meta[property=og:image]").attr("abs:content"));
                for (Element img : d.select("img")) {
                    String indice = (img.attr("src") + " " + img.attr("alt") + " " + img.attr("class") + " " + img.id()).toLowerCase(Locale.ROOT);
                    if (indice.contains("logo")) ajouterLogo(logos, img.absUrl("src"));
                }
            }
            if (p == pages.get(0)) ajouterPhoto(photos, d.select("meta[property=og:image]").attr("abs:content"));
            for (Element img : d.select("img")) {
                if (dansMenuOuPied(img) || tropPetite(img)) continue;
                String source = meilleureSource(img);
                if (source == null) continue;
                // On juge le chemin de la vraie image (pas un « placeholder » de chargement différé, ni le nom de domaine)
                String indice = (cheminDe(source) + " " + img.attr("alt") + " " + img.attr("class") + " " + img.id()).toLowerCase(Locale.ROOT);
                if (INDICES_NON_PHOTO.matcher(indice).find()) continue;
                ajouterPhoto(photos, source);
            }
            int nbJson = 0;
            for (Element s : d.select("script[type=application/ld+json]")) {
                if (nbJson++ >= 2 || json.length() > 4000) break;
                json.append(coupe(s.data().replaceAll("\\s+", " ").trim(), 2500)).append('\n');
            }
            if (texte.length() < TEXTE_TOTAL) {
                texte.append("--- Page : ").append(p.url()).append(" ---\n").append(texteUtile(d)).append('\n');
            }
        }
        return new ContenuSite(url, accueil.title().trim(), description, meta(accueil, "meta[property=og:site_name]"),
                new ArrayList<>(couleurs), reseaux, whatsapp, new ArrayList<>(telephones), new ArrayList<>(logos).subList(0, Math.min(5, logos.size())), photosRetenues(photos, logos),
                json.toString().trim(), coupe(texte.toString(), TEXTE_TOTAL), lues);
    }

    /** Titres, paragraphes et listes, sans menus ni scripts, sans doublons ni lignes vides. */
    static String texteUtile(Document d) {
        Document copie = d.clone();
        copie.select("script, style, noscript, svg, iframe, form, nav, button, select, option").remove();
        Element racine = copie.selectFirst("main");
        if (racine == null) racine = copie.body();
        if (racine == null) return "";
        StringBuilder sb = new StringBuilder();
        String precedent = "";
        for (Element e : racine.select("h1, h2, h3, h4, p, li, address, td, dd")) {
            String t = e.text();
            t = t.replaceAll("\\s+", " ").trim();
            if (t.length() < 3 || t.equals(precedent)) continue;
            sb.append(t).append('\n');
            precedent = t;
            if (sb.length() > TEXTE_PAR_PAGE) break;
        }
        return coupe(sb.toString(), TEXTE_PAR_PAGE);
    }

    // ------------------------------------------------------------------ petits outils

    static String hex(String valeur) {
        if (valeur == null) return null;
        Matcher m = HEX.matcher(valeur.trim());
        if (!m.matches()) return null;
        String c = m.group(1).toLowerCase(Locale.ROOT);
        if (c.length() == 3) c = "" + c.charAt(0) + c.charAt(0) + c.charAt(1) + c.charAt(1) + c.charAt(2) + c.charAt(2);
        return "#" + c;
    }

    /** FACEBOOK, INSTAGRAM, TIKTOK ou LINKEDIN pour un lien vers un profil ou une page (pas un bouton de partage). */
    static String reseau(String href) {
        if (href == null) return null;
        String h = href.toLowerCase(Locale.ROOT);
        if (!h.startsWith("http")) return null;
        if (h.matches(".*(/sharer|/share|/intent/|/dialog/|/plugins/|/login|/tr\\?|/oauth).*")) return null;
        String reseau = h.contains("facebook.com/") || h.contains("fb.com/") ? "FACEBOOK"
                : h.contains("instagram.com/") ? "INSTAGRAM"
                : h.contains("tiktok.com/") ? "TIKTOK"
                : h.contains("linkedin.com/") ? "LINKEDIN" : null;
        if (reseau == null) return null;
        String chemin = h.replaceFirst("^https?://[^/]+", "").replaceAll("[?#].*$", "");
        return chemin.replace("/", "").isBlank() ? null : reseau;
    }

    /** Numéro WhatsApp d'un lien wa.me ou api.whatsapp.com, au format +chiffres. */
    static String whatsapp(String href) {
        if (href == null) return null;
        Matcher m = Pattern.compile("(?i)wa\\.me/\\+?(\\d{7,15})").matcher(href);
        if (m.find()) return "+" + m.group(1);
        m = Pattern.compile("(?i)whatsapp\\.com/send[^\"]*?phone=\\+?(\\d{7,15})").matcher(href);
        return m.find() ? "+" + m.group(1) : null;
    }

    private static void ajouterLogo(Set<String> logos, String url) {
        if (url == null || !url.toLowerCase(Locale.ROOT).startsWith("http")) return;
        String u = url.toLowerCase(Locale.ROOT).replaceAll("[?#].*$", "");
        if (!u.endsWith(".ico") && !u.endsWith(".svg")) logos.add(url); // l'icône et le vectoriel ne servent pas aux affiches
    }

    // ------------------------------------------------------------------ photos

    private static final Pattern INDICES_NON_PHOTO = Pattern.compile(
            "logo|icon|sprite|avatar|emoji|pixel|tracking|loader|spinner|placeholder|flag|badge|payment|visa|mastercard|paypal|banner-ad|captcha");

    private static boolean dansMenuOuPied(Element img) {
        for (Element e = img.parent(); e != null; e = e.parent()) {
            if (e.tagName().equals("nav") || e.tagName().equals("footer")) return true;
        }
        return false;
    }

    /** Dimensions déclarées dans la page : une vignette ou une icône (moins de 200 px) n'est pas une photo. */
    private static boolean tropPetite(Element img) {
        int w = entier(img.attr("width"));
        int h = entier(img.attr("height"));
        return (w > 0 && w < 200) || (h > 0 && h < 200);
    }

    private static int entier(String v) {
        Matcher m = Pattern.compile("^\\s*(\\d{1,5})").matcher(v == null ? "" : v);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** La plus grande version proposée dans srcset, sinon les attributs des chargements différés, sinon src. */
    static String meilleureSource(Element img) {
        String srcset = img.attr("srcset").isBlank() ? img.attr("data-srcset") : img.attr("srcset");
        String meilleure = null;
        double max = -1;
        for (String entree : srcset.split(",")) {
            String[] parties = entree.trim().split("\\s+");
            if (parties[0].isBlank()) continue;
            double valeur = parties.length > 1 ? entier(parties[1]) : 1;
            if (valeur > max) {
                max = valeur;
                try {
                    meilleure = java.net.URI.create(img.baseUri()).resolve(parties[0]).toString();
                } catch (IllegalArgumentException e) {
                    // adresse invalide : on garde la précédente
                }
            }
        }
        if (meilleure != null) return meilleure;
        for (String attribut : List.of("data-src", "data-lazy-src", "data-original", "src")) {
            String u = img.absUrl(attribut);
            if (!u.isBlank()) return u;
        }
        return null;
    }

    private static void ajouterPhoto(Set<String> photos, String url) {
        if (url == null || !url.toLowerCase(Locale.ROOT).startsWith("http")) return;
        String u = url.toLowerCase(Locale.ROOT).replaceAll("[?#].*$", "");
        if (u.endsWith(".svg") || u.endsWith(".gif") || u.endsWith(".ico")) return;
        photos.add(url);
    }

    /** L'image de partage du site peut être à la fois un logo possible et une photo : elle est proposée dans les deux listes. */
    private static List<String> photosRetenues(Set<String> photos, Set<String> logos) {
        return photos.stream().limit(12).toList();
    }

    private static String cheminDe(String url) {
        try {
            String chemin = java.net.URI.create(url).getPath();
            return chemin == null ? "" : chemin;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static String meta(Document d, String selecteur) {
        Element e = d.selectFirst(selecteur);
        return e == null ? "" : e.attr("content").trim();
    }

    static String coupe(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String sansWww(String hote) {
        String h = hote.toLowerCase(Locale.ROOT);
        return h.startsWith("www.") ? h.substring(4) : h;
    }
}
