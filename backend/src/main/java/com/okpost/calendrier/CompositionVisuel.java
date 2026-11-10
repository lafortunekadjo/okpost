package com.okpost.calendrier;

import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Habille une image générée avec ce qui doit être exact : le logo de la fiche (en haut à gauche)
 * et un bandeau de coordonnées à icônes, aux couleurs de la marque. Ces éléments sont dessinés par OkPost, pas par l'IA.
 */
@Component
public class CompositionVisuel {

    public enum TypeContact { SITE, WHATSAPP, TELEPHONE, RESEAU }

    /** Un élément du bandeau : icône, petit libellé facultatif (ex. « TikTok · Instagram ») et valeur. */
    public record Contact(TypeContact type, String libelle, String valeur) {}

    public record Habillage(byte[] logo, String couleur, List<Contact> contacts) {}

    /** Hauteur du bandeau en % de la LARGEUR de l'image (sert aussi à réserver la place dans le prompt). */
    public static float hauteurBandeau(int nbContacts) {
        if (nbContacts <= 0) return 0;
        return nbContacts > 3 ? 17f : 11f;
    }

    /** Recadre au centre vers le ratio demandé (ex. image carrée → 4:5), sans déformer. */
    public byte[] recadrer(byte[] image, String ratio) {
        BufferedImage src = lire(image);
        if (src == null || ratio == null || !ratio.matches("\\d+:\\d+")) return image;
        String[] r = ratio.split(":");
        double cible = Double.parseDouble(r[0]) / Double.parseDouble(r[1]);
        double actuel = (double) src.getWidth() / src.getHeight();
        if (Math.abs(cible - actuel) < 0.02) return image;
        int w = src.getWidth();
        int h = src.getHeight();
        int nw = cible < actuel ? (int) Math.round(h * cible) : w;
        int nh = cible < actuel ? h : (int) Math.round(w / cible);
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, -(w - nw) / 2, -(h - nh) / 2, null);
        g.dispose();
        return jpeg(out);
    }

    public byte[] habiller(byte[] image, Habillage h) {
        BufferedImage src = lire(image);
        if (src == null) {
            return image;
        }
        int w = src.getWidth();
        int hauteur = src.getHeight();
        float u = w / 100f;
        Color marque = couleur(h.couleur(), new Color(17, 24, 39));

        BufferedImage out = new BufferedImage(w, hauteur, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.drawImage(src, 0, 0, null);

        List<Contact> contacts = h.contacts() == null ? List.of()
                : h.contacts().stream().filter(c -> c.valeur() != null && !c.valeur().isBlank()).limit(6).toList();
        if (!contacts.isEmpty()) {
            dessinerBandeau(g, w, hauteur, u, marque, contacts);
        }
        BufferedImage logo = h.logo() == null ? null : lire(h.logo());
        if (logo != null) {
            dessinerLogo(g, logo, w, hauteur, u);
        }
        g.dispose();
        return jpeg(out);
    }

    // ------------------------------------------------------------------ bandeau

    private void dessinerBandeau(Graphics2D g, int w, int hauteur, float u, Color marque, List<Contact> contacts) {
        int bandeau = Math.round(u * hauteurBandeau(contacts.size()));
        int y0 = hauteur - bandeau;
        Color fonce = assombrir(marque, 0.72f);
        g.setPaint(new GradientPaint(0, y0, marque, w, hauteur, fonce));
        g.fillRect(0, y0, w, bandeau);
        // Liseré clair en haut du bandeau
        g.setColor(new Color(255, 255, 255, 70));
        g.fillRect(0, y0, w, Math.max(1, Math.round(u * 0.35f)));

        int lignesMax = contacts.size() > 3 ? 2 : 1;
        float marge = u * 4;
        float espace = u * 3.2f;
        Font fValeur = new Font(Font.SANS_SERIF, Font.BOLD, Math.round(u * 3.1f));
        List<List<Contact>> lignes = repartir(g, contacts, fValeur, u, w - 2 * marge, espace, lignesMax);
        while (lignes.size() > lignesMax && fValeur.getSize() > u * 1.8f) {
            fValeur = fValeur.deriveFont(fValeur.getSize2D() * 0.9f);
            lignes = repartir(g, contacts, fValeur, u, w - 2 * marge, espace, lignesMax);
        }
        Font fLibelle = fValeur.deriveFont(Font.PLAIN, fValeur.getSize2D() * 0.82f);
        float hautLigne = fValeur.getSize2D() * 1.9f;
        float yLigne = y0 + (bandeau - hautLigne * lignes.size()) / 2f;
        for (List<Contact> ligne : lignes) {
            float largeur = largeurLigne(g, ligne, fValeur, fLibelle, u, espace);
            float x = (w - largeur) / 2f;
            float centre = yLigne + hautLigne / 2f;
            for (Contact c : ligne) {
                x = dessinerContact(g, c, x, centre, fValeur, fLibelle, u, marque) + espace;
            }
            yLigne += hautLigne;
        }
    }

    private List<List<Contact>> repartir(Graphics2D g, List<Contact> contacts, Font fValeur, float u,
                                         float largeurMax, float espace, int lignesMax) {
        Font fLibelle = fValeur.deriveFont(Font.PLAIN, fValeur.getSize2D() * 0.82f);
        List<List<Contact>> lignes = new ArrayList<>();
        List<Contact> courante = new ArrayList<>();
        float occupe = 0;
        for (Contact c : contacts) {
            float l = largeurContact(g, c, fValeur, fLibelle, u);
            if (!courante.isEmpty() && occupe + espace + l > largeurMax) {
                lignes.add(courante);
                courante = new ArrayList<>();
                occupe = 0;
            }
            occupe += (courante.isEmpty() ? 0 : espace) + l;
            courante.add(c);
            if (occupe > largeurMax && courante.size() == 1 && lignesMax == 1) {
                // élément seul trop large : la boucle appelante réduira la police
                lignes.add(courante);
                courante = new ArrayList<>();
                occupe = 0;
            }
        }
        if (!courante.isEmpty()) lignes.add(courante);
        return lignes;
    }

    private float largeurLigne(Graphics2D g, List<Contact> ligne, Font fv, Font fl, float u, float espace) {
        float total = 0;
        for (Contact c : ligne) total += largeurContact(g, c, fv, fl, u);
        return total + espace * (ligne.size() - 1);
    }

    private float largeurContact(Graphics2D g, Contact c, Font fv, Font fl, float u) {
        float icone = fv.getSize2D() * 1.55f;
        float texte = g.getFontMetrics(fv).stringWidth(c.valeur());
        if (c.libelle() != null && !c.libelle().isBlank()) {
            texte += g.getFontMetrics(fl).stringWidth(c.libelle() + "  ");
        }
        return icone + fv.getSize2D() * 0.55f + texte;
    }

    /** Pastille blanche avec l'icône, puis libellé discret et valeur en gras. Renvoie la position x de fin. */
    private float dessinerContact(Graphics2D g, Contact c, float x, float centre, Font fv, Font fl, float u, Color marque) {
        float d = fv.getSize2D() * 1.55f;
        g.setColor(new Color(255, 255, 255, 240));
        g.fill(new Ellipse2D.Float(x, centre - d / 2, d, d));
        dessinerIcone(g, c.type(), x + d / 2, centre, d * 0.56f, marque);
        float tx = x + d + fv.getSize2D() * 0.55f;
        if (c.libelle() != null && !c.libelle().isBlank()) {
            g.setFont(fl);
            FontMetrics fm = g.getFontMetrics();
            g.setColor(new Color(255, 255, 255, 210));
            String l = c.libelle() + "  ";
            g.drawString(l, tx, centre + (fm.getAscent() - fm.getDescent()) / 2f);
            tx += fm.stringWidth(l);
        }
        g.setFont(fv);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Color.WHITE);
        g.drawString(c.valeur(), tx, centre + (fm.getAscent() - fm.getDescent()) / 2f);
        return tx + fm.stringWidth(c.valeur());
    }

    /** Icônes génériques dessinées en vectoriel : globe, téléphone, bulle d'appel, arobase. */
    private void dessinerIcone(Graphics2D g, TypeContact type, float cx, float cy, float t, Color couleur) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(couleur);
        float r = t / 2;
        g2.setStroke(new BasicStroke(Math.max(1f, t * 0.11f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (type) {
            case SITE -> {
                g2.draw(new Ellipse2D.Float(cx - r, cy - r, t, t));
                g2.draw(new Ellipse2D.Float(cx - r * 0.42f, cy - r, r * 0.84f, t));
                g2.draw(new Line2D.Float(cx - r, cy, cx + r, cy));
                g2.draw(new Arc2D.Float(cx - r * 0.87f, cy - r * 1.35f, r * 1.74f, r * 1.2f, 200, 140, Arc2D.OPEN));
                g2.draw(new Arc2D.Float(cx - r * 0.87f, cy + r * 0.15f, r * 1.74f, r * 1.2f, 20, 140, Arc2D.OPEN));
            }
            case WHATSAPP -> {
                // Bulle de discussion avec un combiné
                g2.draw(new Ellipse2D.Float(cx - r, cy - r, t, t));
                GeneralPath queue = new GeneralPath();
                queue.moveTo(cx - r * 0.62f, cy + r * 0.78f);
                queue.lineTo(cx - r * 0.95f, cy + r * 1.05f);
                queue.lineTo(cx - r * 0.25f, cy + r * 0.97f);
                g2.draw(queue);
                combine(g2, cx, cy, r * 0.62f);
            }
            case TELEPHONE -> combine(g2, cx, cy, r * 0.95f);
            case RESEAU -> {
                Font f = new Font(Font.SANS_SERIF, Font.BOLD, Math.round(t * 1.15f));
                g2.setFont(f);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("@", cx - fm.stringWidth("@") / 2f, cy + (fm.getAscent() - fm.getDescent()) / 2f);
            }
        }
        g2.dispose();
    }

    /** Combiné téléphonique stylisé. */
    private static void combine(Graphics2D g, float cx, float cy, float r) {
        GeneralPath p = new GeneralPath();
        p.moveTo(cx - r * 0.75f, cy - r * 0.95f);
        p.quadTo(cx - r * 1.05f, cy - r * 0.2f, cx - r * 0.35f, cy + r * 0.45f);
        p.quadTo(cx + r * 0.25f, cy + r * 1.05f, cx + r * 0.95f, cy + r * 0.75f);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setStroke(new BasicStroke(r * 0.42f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(p);
        g2.dispose();
    }

    // ------------------------------------------------------------------ logo

    private void dessinerLogo(Graphics2D g, BufferedImage logo, int w, int hauteur, float u) {
        float maxL = w * 0.24f;
        float maxH = hauteur * 0.09f;
        float echelle = Math.min(maxL / logo.getWidth(), maxH / logo.getHeight());
        int lw = Math.max(1, Math.round(logo.getWidth() * echelle));
        int lh = Math.max(1, Math.round(logo.getHeight() * echelle));
        int marge = Math.round(u * 4);
        int pad = Math.round(u * 1.6f);
        int arrondi = Math.round(u * 2.6f);
        g.setColor(new Color(0, 0, 0, 35));
        g.fillRoundRect(marge + 2, marge + 3, lw + pad * 2, lh + pad * 2, arrondi, arrondi);
        g.setComposite(AlphaComposite.SrcOver.derive(0.96f));
        g.setColor(Color.WHITE);
        g.fillRoundRect(marge, marge, lw + pad * 2, lh + pad * 2, arrondi, arrondi);
        g.setComposite(AlphaComposite.SrcOver);
        g.drawImage(logo, marge + pad, marge + pad, lw, lh, null);
    }

    // ------------------------------------------------------------------ outils

    static Color couleur(String hex, Color parDefaut) {
        if (hex == null || !hex.trim().matches("#?[0-9a-fA-F]{6}")) return parDefaut;
        return new Color(Integer.parseInt(hex.trim().replace("#", ""), 16));
    }

    private static Color assombrir(Color c, float f) {
        return new Color(Math.round(c.getRed() * f), Math.round(c.getGreen() * f), Math.round(c.getBlue() * f));
    }

    private static BufferedImage lire(byte[] octets) {
        try {
            return ImageIO.read(new ByteArrayInputStream(octets));
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] jpeg(BufferedImage img) {
        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream();
             ImageOutputStream flux = ImageIO.createImageOutputStream(sortie)) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam p = writer.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(0.92f);
            writer.setOutput(flux);
            writer.write(null, new IIOImage(img, null, null), p);
            writer.dispose();
            flux.flush();
            return sortie.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
