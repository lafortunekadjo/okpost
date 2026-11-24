package com.okpost.calendrier;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Compose l'affiche : une photo (banque du client, photo fournie ou fond généré par l'IA) + un gabarit de mise en page
 * (titre, chiffre, texte, bouton) + le logo et le bandeau de coordonnées de CompositionVisuel.
 * Tout le texte est dessiné par OkPost : il est toujours exact, lisible et aux couleurs de la marque.
 */
@Component
public class MoteurAffiche {

    public enum Gabarit {
        /** Gros chiffre dans un encadré (« 850 utilisateurs », « 0 F », « -30 % »). */
        CHIFFRE("the subject in the right half and lower part of the frame, the left side calmer"),
        /** Bandeau sombre en haut avec le titre, photo en dessous. */
        TITRE_HAUT("the subject centred in the lower two thirds of the frame, the top fifth calm"),
        /** Panneau de couleur en diagonale à gauche, photo à droite. */
        DIAGONALE("the subject on the right side of the frame, the left side calmer"),
        /** Photo pleine page, texte en bas sur un dégradé : le gabarit des carrousels et des stories. */
        PLEIN_CADRE("the subject in the upper half of the frame, the lower third calm and simple"),
        /** Fiche produit : la photo du produit reste entière, pastille de prix en haut, nom et bouton de commande en bas. */
        PRODUIT("the product centred and filling most of the frame, the lower third calm");

        /** Cadrage à demander à l'IA pour que le sujet ne passe pas sous le texte. */
        public final String cadrage;

        Gabarit(String cadrage) {
            this.cadrage = cadrage;
        }

        public static Gabarit depuis(String nom) {
            if (nom == null || nom.isBlank()) return null;
            try {
                return valueOf(nom.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    public record Contenu(Gabarit gabarit, String titre, String chiffre, String texte, String cta) {}

    /** Gabarit demandé s'il est valide et possible, sinon celui qui convient au contenu. */
    public static Gabarit resoudre(String demande, String duBrief, String chiffre, String texte) {
        boolean aChiffre = chiffre != null && !chiffre.isBlank();
        for (String nom : new String[]{demande, duBrief}) {
            Gabarit g = Gabarit.depuis(nom);
            if (g == null) continue;
            if (g == Gabarit.CHIFFRE && !aChiffre) continue;
            return g;
        }
        if (aChiffre) return Gabarit.CHIFFRE;
        return texte != null && !texte.isBlank() ? Gabarit.TITRE_HAUT : Gabarit.PLEIN_CADRE;
    }

    private static final int LARGEUR = 1080;

    private final Font gras = charger("Poppins-Bold.ttf", Font.BOLD);
    private final Font semi = charger("Poppins-SemiBold.ttf", Font.BOLD);

    private final CompositionVisuel composition;

    public MoteurAffiche(CompositionVisuel composition) {
        this.composition = composition;
    }

    private static Font charger(String nom, int styleSecours) {
        try (InputStream in = MoteurAffiche.class.getResourceAsStream("/fonts/" + nom)) {
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Exception e) {
            return new Font(Font.SANS_SERIF, styleSecours, 12);
        }
    }

    /** Dimensions du visuel final pour un ratio (largeur fixe, bonne définition pour les réseaux). */
    static int[] dimensions(String ratio) {
        int l = 1;
        int h = 1;
        if (ratio != null && ratio.matches("\\d+:\\d+")) {
            String[] r = ratio.split(":");
            l = Math.max(1, Integer.parseInt(r[0]));
            h = Math.max(1, Integer.parseInt(r[1]));
        }
        return new int[]{LARGEUR, Math.round(LARGEUR * (float) h / l)};
    }

    public byte[] composer(byte[] photo, String ratio, Contenu c, CompositionVisuel.Habillage marque) {
        BufferedImage src = lire(photo);
        if (src == null) return photo;
        int[] d = dimensions(ratio);
        int w = d[0];
        int h = d[1];
        float u = w / 100f;
        Color couleur = CompositionVisuel.couleur(marque == null ? null : marque.couleur(), new Color(37, 99, 235));
        Palette p = new Palette(couleur);
        int nbContacts = marque == null || marque.contacts() == null ? 0
                : (int) marque.contacts().stream().filter(x -> x.valeur() != null && !x.valeur().isBlank()).limit(6).count();
        float bandeau = u * CompositionVisuel.hauteurBandeau(nbContacts);
        float haut = hauteurLogo(marque == null ? null : marque.logo(), w, h, u);

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        couvrir(g, src, w, h);

        Zone z = new Zone(w, h, u, haut, h - bandeau);
        String titre = nettoyer(c.titre());
        String chiffre = nettoyer(c.chiffre());
        String texte = nettoyer(c.texte());
        String cta = nettoyer(c.cta());
        Gabarit gabarit = resoudre(c.gabarit() == null ? null : c.gabarit().name(), null, chiffre, texte);
        switch (gabarit) {
            case CHIFFRE -> chiffre(g, z, p, titre, chiffre, texte, cta);
            case TITRE_HAUT -> titreHaut(g, z, p, titre, texte, cta);
            case DIAGONALE -> diagonale(g, z, p, titre, texte, cta);
            case PLEIN_CADRE -> pleinCadre(g, z, p, titre, texte, cta);
            case PRODUIT -> produit(g, z, p, titre, chiffre, texte, cta);
        }
        g.dispose();
        return composition.habiller(png(out), marque == null ? new CompositionVisuel.Habillage(null, null, List.of()) : marque);
    }

    // ------------------------------------------------------------------ gabarits

    /** Zone utile : sous le logo, au-dessus du bandeau de coordonnées. */
    private record Zone(int w, int h, float u, float haut, float bas) {}

    private record Palette(Color marque, Color fond, Color accent) {
        Palette(Color marque) {
            this(marque, assombrir(marque, 0.16f), eclaircir(marque));
        }
    }

    private void chiffre(Graphics2D g, Zone z, Palette p, String titre, String chiffre, String texte, String cta) {
        float u = z.u();
        g.setPaint(new GradientPaint(0, 0, alpha(p.fond(), 0.9f), z.w() * 0.66f, 0, alpha(p.fond(), 0f)));
        g.fillRect(0, 0, z.w(), z.h());
        g.setPaint(new GradientPaint(0, z.h() * 0.55f, alpha(p.fond(), 0f), 0, z.bas(), alpha(p.fond(), 0.6f)));
        g.fillRect(0, Math.round(z.h() * 0.55f), z.w(), z.h());

        float cx = 3 * u;
        float cw = 62 * u;
        float pad = 3.2f * u;
        Bloc nb = ajuster(g, gras, chiffre, cw - 2 * pad, 26 * u, 26 * u, 6 * u, 1.0f, 1);
        Bloc tb = titre.isEmpty() ? null : ajuster(g, gras, titre, cw - 2 * pad, 16 * u, 7.4f * u, 3.4f * u, 1.12f, 2);
        float ch = pad + nb.hauteur() + (tb == null ? 0 : 0.6f * u + tb.hauteur()) + pad * 0.8f;
        float cy = z.haut();
        g.setColor(alpha(p.fond(), 0.93f));
        g.fill(new RoundRectangle2D.Float(cx, cy, cw, ch, 3 * u, 3 * u));
        g.setColor(p.accent());
        g.setStroke(new BasicStroke(0.5f * u));
        g.draw(new RoundRectangle2D.Float(cx, cy, cw, ch, 3 * u, 3 * u));
        float y = cy + pad * 0.7f;
        y = dessiner(g, nb, cx + pad, y, p.accent(), false);
        if (tb != null) dessiner(g, tb, cx + pad, y + 0.6f * u, Color.WHITE, false);

        float y2 = cy + ch + 3 * u;
        float libre = z.bas() - 3 * u - y2;
        float lc = 42 * u;
        if (!texte.isEmpty() && libre > 6 * u) {
            Bloc b = ajuster(g, semi, texte, lc, libre * 0.5f, 3.8f * u, 2.6f * u, 1.3f, 5);
            y2 = dessiner(g, b, 4 * u, y2, Color.WHITE, true) + 2 * u;
        }
        if (!cta.isEmpty() && libre > 12 * u) {
            g.setColor(p.accent());
            g.fillRect(Math.round(4 * u), Math.round(y2), Math.round(20 * u), Math.max(2, Math.round(0.6f * u)));
            Bloc b = ajuster(g, gras, cta, lc, Math.max(6 * u, z.bas() - 3 * u - (y2 + 2 * u)), 4.8f * u, 2.8f * u, 1.2f, 3);
            dessiner(g, b, 4 * u, y2 + 2.2f * u, Color.WHITE, true);
        }
    }

    private void titreHaut(Graphics2D g, Zone z, Palette p, String titre, String texte, String cta) {
        float u = z.u();
        float x = 4 * u;
        float largeur = 92 * u;
        Bloc tb = titre.isEmpty() ? null : ajuster(g, gras, titre, largeur, z.h() * 0.22f, 9.4f * u, 4.4f * u, 1.1f, 3);
        Bloc xb = texte.isEmpty() ? null : ajuster(g, semi, texte, 80 * u, z.h() * 0.13f, 3.8f * u, 2.6f * u, 1.3f, 4);
        float y = z.haut();
        float contenu = (tb == null ? 0 : tb.hauteur()) + (xb == null ? 0 : xb.hauteur() + 1.6f * u);
        float bas = y + contenu + 3 * u;
        g.setColor(p.fond());
        g.fillRect(0, 0, z.w(), Math.round(bas));
        g.setPaint(new GradientPaint(0, bas, alpha(p.fond(), 0.9f), 0, bas + 7 * u, alpha(p.fond(), 0f)));
        g.fillRect(0, Math.round(bas), z.w(), Math.round(7 * u));
        // Décor discret : angle de la couleur de marque en haut à droite
        g.setColor(alpha(p.marque(), 0.55f));
        g.fill(new Polygon(new int[]{Math.round(z.w() * 0.80f), z.w(), z.w()},
                new int[]{0, 0, Math.round(z.w() * 0.12f)}, 3));
        if (tb != null) y = dessiner(g, tb, x, y, Color.WHITE, false);
        if (xb != null) y = dessiner(g, xb, x, y + 1.6f * u, new Color(235, 238, 245), false);
        // Dégradé de lisibilité en bas de la photo
        g.setPaint(new GradientPaint(0, z.h() * 0.7f, alpha(p.fond(), 0f), 0, z.bas(), alpha(p.fond(), 0.5f)));
        g.fillRect(0, Math.round(z.h() * 0.7f), z.w(), z.h());
        if (!cta.isEmpty()) pilule(g, cta, x, bas + 1.5f * u, 3.6f * u, p, 90 * u);
    }

    private void diagonale(Graphics2D g, Zone z, Palette p, String titre, String texte, String cta) {
        float u = z.u();
        float x = 4 * u;
        Bloc tb = titre.isEmpty() ? null : ajuster(g, gras, titre, 44 * u, z.h() * 0.34f, 8.6f * u, 4.2f * u, 1.1f, 4);
        float y = z.haut();
        float hp = y + (tb == null ? 0 : tb.hauteur()) + 5 * u;
        Polygon panneau = new Polygon(
                new int[]{0, Math.round(z.w() * 0.66f), Math.round(z.w() * 0.52f), 0},
                new int[]{0, 0, Math.round(hp), Math.round(hp)}, 4);
        g.setColor(alpha(p.marque(), 0.96f));
        g.fill(panneau);
        g.setColor(alpha(Color.WHITE, 0.18f));
        g.setStroke(new BasicStroke(0.5f * u));
        g.drawLine(Math.round(z.w() * 0.66f), 0, Math.round(z.w() * 0.52f), Math.round(hp));
        Color surMarque = contraste(p.marque());
        float suite = hp;
        if (tb != null) dessiner(g, tb, x, y, surMarque, false);
        if (!texte.isEmpty()) {
            Bloc xb = ajuster(g, semi, texte, 42 * u, z.h() * 0.2f, 3.7f * u, 2.6f * u, 1.3f, 5);
            float bh = xb.hauteur() + 3.4f * u;
            g.setColor(alpha(p.fond(), 0.88f));
            g.fillRect(0, Math.round(hp), Math.round(50 * u), Math.round(bh));
            dessiner(g, xb, x, hp + 1.7f * u, Color.WHITE, false);
            suite = hp + bh;
        }
        g.setPaint(new GradientPaint(0, z.h() * 0.7f, alpha(p.fond(), 0f), 0, z.bas(), alpha(p.fond(), 0.45f)));
        g.fillRect(0, Math.round(z.h() * 0.7f), z.w(), z.h());
        if (!cta.isEmpty()) pilule(g, cta, 3 * u, suite + 3 * u, 3.6f * u, p, 60 * u);
    }

    /** Le produit reste lisible : pas de voile sur la photo, seulement une pastille de prix, le nom et le bouton en bas. */
    private void produit(Graphics2D g, Zone z, Palette p, String titre, String prix, String texte, String cta) {
        float u = z.u();
        pleinCadre(g, z, p, titre, texte, cta);
        if (prix.isEmpty()) return;
        Bloc pb = ajuster(g, gras, prix, 40 * u, 9 * u, 7 * u, 3.6f * u, 1.0f, 1);
        float pad = 2.2f * u;
        float largeur = pb.lignes().stream().map(l -> pb.mesures().stringWidth(l)).max(Integer::compare).orElse(0) + 2 * pad;
        float hauteur = pb.hauteur() + 1.6f * pad;
        float x = 4 * u;
        float y = z.haut() + 1.5f * u;
        g.setColor(p.marque());
        g.fill(new RoundRectangle2D.Float(x, y, largeur, hauteur, hauteur / 2, hauteur / 2));
        dessiner(g, pb, x + pad, y + 0.8f * pad, contraste(p.marque()), false);
    }

    private void pleinCadre(Graphics2D g, Zone z, Palette p, String titre, String texte, String cta) {
        float u = z.u();
        g.setPaint(new GradientPaint(0, 0, alpha(Color.BLACK, 0.45f), 0, z.h() * 0.22f, alpha(Color.BLACK, 0f)));
        g.fillRect(0, 0, z.w(), Math.round(z.h() * 0.22f));
        float debut = z.h() * 0.42f;
        g.setPaint(new GradientPaint(0, debut, alpha(p.fond(), 0f), 0, z.bas() - 6 * u, alpha(p.fond(), 0.93f)));
        g.fillRect(0, Math.round(debut), z.w(), Math.round(z.h() - debut));

        float x = 4 * u;
        float bas = z.bas() - 3.5f * u;
        float dispo = bas - z.haut();
        float y = bas;
        if (!cta.isEmpty()) {
            float hp = pilule(g, cta, x, -1000, 3.6f * u, p, 90 * u);
            pilule(g, cta, x, y - hp, 3.6f * u, p, 90 * u);
            y -= hp + 2.4f * u;
        }
        if (!texte.isEmpty()) {
            Bloc xb = ajuster(g, semi, texte, 84 * u, Math.min(dispo * 0.3f, z.h() * 0.16f), 3.9f * u, 2.6f * u, 1.3f, 5);
            y -= xb.hauteur();
            dessiner(g, xb, x, y, new Color(235, 238, 245), true);
            y -= 2 * u;
        }
        if (!titre.isEmpty()) {
            Bloc tb = ajuster(g, gras, titre, 90 * u, Math.max(8 * u, y - z.haut() - 2 * u), 10.4f * u, 4.4f * u, 1.1f, 4);
            y -= tb.hauteur();
            g.setColor(p.accent());
            g.fillRect(Math.round(x), Math.round(y - 2.2f * u), Math.round(12 * u), Math.max(2, Math.round(0.8f * u)));
            dessiner(g, tb, x, y, Color.WHITE, true);
        }
    }

    // ------------------------------------------------------------------ texte

    private record Bloc(Font police, List<String> lignes, float interligne, FontMetrics mesures) {
        float hauteur() {
            return lignes.size() * police.getSize2D() * interligne;
        }
    }

    /** Plus grande taille (entre max et min) pour laquelle le texte tient en largeur, hauteur et nombre de lignes. */
    private Bloc ajuster(Graphics2D g, Font base, String texte, float largeur, float hauteurMax, float max, float min,
                         float interligne, int maxLignes) {
        Bloc dernier = null;
        for (float t = max; ; t = Math.max(min, t - Math.max(1f, max * 0.05f))) {
            Font f = base.deriveFont(t);
            FontMetrics fm = g.getFontMetrics(f);
            List<String> lignes = couper(fm, texte, largeur);
            dernier = new Bloc(f, lignes, interligne, fm);
            boolean largeOk = lignes.stream().allMatch(l -> fm.stringWidth(l) <= largeur + 1);
            if ((largeOk && lignes.size() <= maxLignes && dernier.hauteur() <= hauteurMax) || t <= min) break;
        }
        return dernier;
    }

    static List<String> couper(FontMetrics fm, String texte, float largeur) {
        List<String> lignes = new ArrayList<>();
        StringBuilder courante = new StringBuilder();
        for (String mot : texte.split("\\s+")) {
            if (mot.isEmpty()) continue;
            String essai = courante.isEmpty() ? mot : courante + " " + mot;
            if (!courante.isEmpty() && fm.stringWidth(essai) > largeur) {
                lignes.add(courante.toString());
                courante = new StringBuilder(mot);
            } else {
                courante = new StringBuilder(essai);
            }
        }
        if (!courante.isEmpty()) lignes.add(courante.toString());
        return lignes;
    }

    /** Dessine le bloc, renvoie le y du bas. */
    private float dessiner(Graphics2D g, Bloc b, float x, float yHaut, Color couleur, boolean ombre) {
        g.setFont(b.police());
        FontMetrics fm = g.getFontMetrics();
        float lh = b.police().getSize2D() * b.interligne();
        float y = yHaut;
        for (String ligne : b.lignes()) {
            float base = y + fm.getAscent() + (lh - fm.getHeight()) / 2f;
            if (ombre) {
                g.setColor(new Color(0, 0, 0, 120));
                g.drawString(ligne, x + b.police().getSize2D() * 0.04f, base + b.police().getSize2D() * 0.05f);
            }
            g.setColor(couleur);
            g.drawString(ligne, x, base);
            y += lh;
        }
        return y;
    }

    /** Bouton arrondi. Renvoie sa hauteur (y très négatif : simple mesure, rien n'est visible). */
    private float pilule(Graphics2D g, String texte, float x, float y, float taille, Palette p, float largeurMax) {
        Font f = gras.deriveFont(taille);
        FontMetrics fm = g.getFontMetrics(f);
        float padH = taille * 0.9f;
        float padV = taille * 0.62f;
        float h = fm.getHeight() + 2 * padV;
        float w = Math.min(largeurMax, fm.stringWidth(texte) + 2 * padH);
        Color fond = luminance(p.marque()) < 0.5f ? p.marque() : p.accent();
        if (luminance(fond) < 0.18f) fond = p.accent();
        g.setColor(fond);
        g.fill(new RoundRectangle2D.Float(x, y, w, h, h, h));
        g.setFont(f);
        g.setColor(contraste(fond));
        String t = texte;
        while (fm.stringWidth(t) > w - 2 * padH && t.length() > 4) t = t.substring(0, t.length() - 2).trim() + "…";
        g.drawString(t, x + (w - fm.stringWidth(t)) / 2f, y + padV + fm.getAscent());
        return h;
    }

    /** Retire émojis et caractères que la police ne sait pas dessiner. */
    static String nettoyer(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\x{1F000}-\\x{1FFFF}\\x{2190}-\\x{2BFF}\\x{FE0F}\\x{200D}\\x{E000}-\\x{F8FF}]", "")
                .replaceAll("\\s+", " ").trim();
    }

    // ------------------------------------------------------------------ images et couleurs

    /** Hauteur occupée en haut par le logo posé par CompositionVisuel (même calcul que dessinerLogo). */
    private static float hauteurLogo(byte[] logo, int w, int h, float u) {
        BufferedImage l = logo == null ? null : lire(logo);
        if (l == null) return 5 * u;
        float echelle = Math.min(w * 0.24f / l.getWidth(), h * 0.09f / l.getHeight());
        return 4 * u + l.getHeight() * echelle + 3.2f * u + 2.5f * u;
    }

    private static void couvrir(Graphics2D g, BufferedImage src, int w, int h) {
        double e = Math.max((double) w / src.getWidth(), (double) h / src.getHeight());
        int sw = (int) Math.ceil(src.getWidth() * e);
        int sh = (int) Math.ceil(src.getHeight() * e);
        g.drawImage(src, (w - sw) / 2, (h - sh) / 2, sw, sh, null);
    }

    private static Color alpha(Color c, float a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(255 * Math.max(0f, Math.min(1f, a))));
    }

    private static Color assombrir(Color c, float f) {
        return new Color(Math.round(c.getRed() * f), Math.round(c.getGreen() * f), Math.round(c.getBlue() * f));
    }

    /** Couleur de marque, éclaircie si elle est trop sombre pour ressortir sur un fond sombre. */
    private static Color eclaircir(Color c) {
        float l = luminance(c);
        if (l >= 0.42f) return c;
        float m = Math.min(0.7f, 0.3f + (0.42f - l) * 1.2f);
        return new Color(Math.round(c.getRed() + (255 - c.getRed()) * m),
                Math.round(c.getGreen() + (255 - c.getGreen()) * m),
                Math.round(c.getBlue() + (255 - c.getBlue()) * m));
    }

    static float luminance(Color c) {
        return (0.2126f * c.getRed() + 0.7152f * c.getGreen() + 0.0722f * c.getBlue()) / 255f;
    }

    /** Blanc sur fond foncé, bleu nuit sur fond clair. */
    private static Color contraste(Color fond) {
        return luminance(fond) > 0.62f ? new Color(17, 24, 39) : Color.WHITE;
    }

    private static BufferedImage lire(byte[] octets) {
        try {
            return ImageIO.read(new ByteArrayInputStream(octets));
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] png(BufferedImage img) {
        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", sortie);
            return sortie.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
