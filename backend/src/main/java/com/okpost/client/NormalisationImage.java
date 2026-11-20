package com.okpost.client;

import com.okpost.commun.ApiException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Prépare une image récupérée sur un site : lecture sûre (taille vérifiée avant le décodage), réduction,
 * puis ré-encodage en JPEG (photos) ou PNG (logos avec transparence). Une image refusée ici n'entre jamais dans la banque.
 */
final class NormalisationImage {

    record Image(byte[] octets, String extension, String mime) {}

    /** Au-delà, l'image pourrait saturer la mémoire du serveur une fois décodée. */
    static final long PIXELS_MAX = 40_000_000L;
    private static final int COTE_PHOTO = 2000;
    private static final int COTE_LOGO = 1200;

    private NormalisationImage() {}

    static Image normaliser(byte[] octets, boolean logo) {
        try (ImageInputStream flux = ImageIO.createImageInputStream(new ByteArrayInputStream(octets))) {
            Iterator<ImageReader> lecteurs = ImageIO.getImageReaders(flux);
            if (!lecteurs.hasNext()) throw ApiException.requeteInvalide("Format d'image non pris en charge");
            ImageReader lecteur = lecteurs.next();
            try {
                lecteur.setInput(flux, true, true);
                long largeur = lecteur.getWidth(0);
                long hauteur = lecteur.getHeight(0);
                if (largeur * hauteur > PIXELS_MAX) throw ApiException.requeteInvalide("Image beaucoup trop grande");
                boolean assezGrande = logo ? Math.min(largeur, hauteur) >= 48 : largeur >= 300 && hauteur >= 200;
                if (!assezGrande) throw ApiException.requeteInvalide("Image trop petite pour être utilisée");
                BufferedImage source = lecteur.read(0);
                return encoder(reduire(source, logo ? COTE_LOGO : COTE_PHOTO), logo && source.getColorModel().hasAlpha());
            } finally {
                lecteur.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException a) throw a;
            throw ApiException.requeteInvalide("Image illisible");
        }
    }

    private static BufferedImage reduire(BufferedImage source, int coteMax) {
        double echelle = Math.min(1.0, (double) coteMax / Math.max(source.getWidth(), source.getHeight()));
        if (echelle >= 1.0) return source;
        int w = Math.max(1, (int) Math.round(source.getWidth() * echelle));
        int h = Math.max(1, (int) Math.round(source.getHeight() * echelle));
        BufferedImage out = new BufferedImage(w, h, source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(source, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static Image encoder(BufferedImage image, boolean png) throws IOException {
        if (png) {
            ByteArrayOutputStream sortie = new ByteArrayOutputStream();
            ImageIO.write(image, "png", sortie);
            return new Image(sortie.toByteArray(), "png", "image/png");
        }
        BufferedImage rvb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rvb.createGraphics();
        g.drawImage(image, 0, 0, Color.WHITE, null);
        g.dispose();
        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream(); ImageOutputStream flux = ImageIO.createImageOutputStream(sortie)) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam p = writer.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(0.9f);
            writer.setOutput(flux);
            writer.write(null, new IIOImage(rvb, null, null), p);
            writer.dispose();
            flux.flush();
            return new Image(sortie.toByteArray(), "jpg", "image/jpeg");
        }
    }
}
