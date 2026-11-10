package com.okpost.calendrier;

import com.okpost.abonnement.AbonnementService;
import com.okpost.calendrier.CalendrierDtos.VisuelVue;
import com.okpost.client.StockageFichiers;
import com.okpost.commun.ApiException;
import com.okpost.securite.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Vidéo défilante d'un carrousel (Facebook n'a pas de carrousel pour les publications de Page) :
 * chaque visuel est posé sur un fond flou au format vertical 9:16, puis enchaîné avec une transition par ffmpeg.
 */
@Service
public class MontageService {

    private static final Logger log = LoggerFactory.getLogger(MontageService.class);
    static final int LARGEUR = 1080;
    static final int HAUTEUR = 1920;
    static final double DUREE_TRANSITION = 0.6;
    static final long MAX_MUSIQUE = 15L * 1024 * 1024;

    private final VisuelGenereRepository visuels;
    private final CalendrierService calendriers;
    private final AbonnementService abonnements;
    private final StockageFichiers stockage;
    private final String ffmpeg;

    public MontageService(VisuelGenereRepository visuels, CalendrierService calendriers, AbonnementService abonnements,
                          StockageFichiers stockage, @Value("${okpost.ffmpeg:ffmpeg}") String ffmpeg) {
        this.visuels = visuels;
        this.calendriers = calendriers;
        this.abonnements = abonnements;
        this.stockage = stockage;
        this.ffmpeg = ffmpeg;
    }

    public VisuelVue monter(Session s, UUID publicationId, Integer secondesDemandees, String transitionDemandee,
                            MultipartFile musique) {
        abonnements.exigerEcriture(s.workspaceId());
        Publication p = calendriers.chargerPublication(s, publicationId);
        List<VisuelGenere> images = imagesDuCarrousel(visuels.findByPublicationIdInOrderByCreatedAtDesc(List.of(p.getId())));
        if (images.size() < 2) {
            throw ApiException.requeteInvalide("Il faut au moins 2 visuels (générés ou téléversés) pour monter la vidéo");
        }
        int secondes = secondesDemandees == null ? 3 : Math.max(2, Math.min(8, secondesDemandees));
        String transition = switch (transitionDemandee == null ? "fondu" : transitionDemandee) {
            case "glissement" -> "slideleft";
            case "zoom" -> "zoomin";
            default -> "fade";
        };
        if (musique != null && !musique.isEmpty() && musique.getSize() > MAX_MUSIQUE) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Musique trop lourde (15 Mo maximum)");
        }

        Path dossier = null;
        try {
            dossier = Files.createTempDirectory("okpost-montage-");
            List<Path> trames = new ArrayList<>();
            for (int i = 0; i < images.size(); i++) {
                BufferedImage source;
                try (InputStream in = stockage.lire(images.get(i).getChemin()).getInputStream()) {
                    source = ImageIO.read(in);
                }
                if (source == null) throw ApiException.requeteInvalide("Visuel illisible : " + (i + 1));
                Path trame = dossier.resolve(String.format(Locale.ROOT, "trame-%02d.jpg", i));
                ecrireJpeg(trame(source), trame);
                trames.add(trame);
            }
            Path audio = null;
            if (musique != null && !musique.isEmpty()) {
                audio = dossier.resolve("musique" + extension(musique.getOriginalFilename()));
                try (InputStream in = musique.getInputStream()) {
                    Files.copy(in, audio);
                }
            }
            Path sortie = dossier.resolve("montage.mp4");
            List<String> commande = commande(ffmpeg, trames, audio, secondes, transition, sortie);
            executer(commande, dossier);
            String chemin;
            try (InputStream in = Files.newInputStream(sortie)) {
                chemin = stockage.enregistrer(in, "mp4");
            }
            VisuelGenere v = new VisuelGenere();
            v.setWorkspaceId(s.workspaceId());
            v.setPublicationId(p.getId());
            v.setChemin(chemin);
            v.setMime("video/mp4");
            v.setSource(VisuelGenere.Source.MONTAGE);
            v.setTypeMedia(VisuelGenere.TypeMedia.VIDEO);
            v.setRatio("9:16");
            v.setTaille(Files.size(sortie));
            v.setNomOrigine("carrousel-" + images.size() + "-visuels.mp4");
            v.setPrompt(images.size() + " visuels, " + secondes + " s chacun, transition " + transitionDemandee
                    + (audio != null ? ", avec musique" : ""));
            return VisuelService.vue(visuels.save(v));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Montage impossible : " + e.getMessage());
        } finally {
            supprimerDossier(dossier);
        }
    }

    /**
     * Visuels du montage : le plus récent de chaque diapositive, dans l'ordre des diapositives ;
     * à défaut de numéros, toutes les images de la publication, de la plus ancienne à la plus récente.
     */
    public static List<VisuelGenere> imagesDuCarrousel(List<VisuelGenere> recentsDabord) {
        List<VisuelGenere> images = recentsDabord.stream()
                .filter(v -> v.getTypeMedia() == VisuelGenere.TypeMedia.IMAGE && v.getSource() != VisuelGenere.Source.MONTAGE)
                .toList();
        Map<Integer, VisuelGenere> parDiapo = new LinkedHashMap<>();
        images.stream().filter(v -> v.getDiapositive() != null).forEach(v -> parDiapo.putIfAbsent(v.getDiapositive(), v));
        if (parDiapo.size() >= 2) {
            return parDiapo.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList();
        }
        List<VisuelGenere> tout = new ArrayList<>(images);
        java.util.Collections.reverse(tout);
        return tout.stream().limit(10).toList();
    }

    static List<String> commande(String ffmpeg, List<Path> trames, Path audio, int secondes, String transition, Path sortie) {
        int n = trames.size();
        double total = n * secondes - (n - 1) * DUREE_TRANSITION;
        List<String> c = new ArrayList<>(List.of(ffmpeg, "-y", "-hide_banner", "-loglevel", "error"));
        for (Path t : trames) {
            c.addAll(List.of("-loop", "1", "-framerate", "30", "-t", String.valueOf(secondes), "-i", t.toString()));
        }
        if (audio != null) c.addAll(List.of("-stream_loop", "-1", "-i", audio.toString()));
        StringBuilder f = new StringBuilder();
        for (int i = 0; i < n; i++) {
            f.append('[').append(i).append(":v]format=yuv420p,setsar=1,fps=30[v").append(i).append("];");
        }
        String precedent = "v0";
        for (int i = 1; i < n; i++) {
            String suivant = i == n - 1 ? "video" : "x" + i;
            double decalage = i * (secondes - DUREE_TRANSITION);
            f.append('[').append(precedent).append("][v").append(i).append("]xfade=transition=").append(transition)
             .append(":duration=").append(DUREE_TRANSITION).append(":offset=")
             .append(String.format(Locale.ROOT, "%.2f", decalage)).append('[').append(suivant).append("];");
            precedent = suivant;
        }
        if (audio != null) {
            f.append('[').append(n).append(":a]afade=t=in:d=0.5,afade=t=out:st=")
             .append(String.format(Locale.ROOT, "%.2f", Math.max(0, total - 1.5))).append(":d=1.5[audio];");
        }
        f.setLength(f.length() - 1);
        c.addAll(List.of("-filter_complex", f.toString(), "-map", "[video]"));
        if (audio != null) c.addAll(List.of("-map", "[audio]", "-c:a", "aac", "-b:a", "128k"));
        c.addAll(List.of("-c:v", "libx264", "-preset", "veryfast", "-crf", "22", "-pix_fmt", "yuv420p", "-r", "30",
                "-movflags", "+faststart", "-t", String.format(Locale.ROOT, "%.2f", total), sortie.toString()));
        return c;
    }

    private static void executer(List<String> commande, Path dossier) throws IOException {
        Path journal = dossier.resolve("ffmpeg.log");
        Process pr;
        try {
            pr = new ProcessBuilder(commande).redirectErrorStream(true).redirectOutput(journal.toFile()).start();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "ffmpeg est introuvable sur le serveur : installe-le, ou indique son chemin dans FFMPEG_PATH");
        }
        try {
            if (!pr.waitFor(5, TimeUnit.MINUTES)) {
                pr.destroyForcibly();
                throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "Montage trop long (plus de 5 minutes)");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pr.destroyForcibly();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Montage interrompu");
        }
        if (pr.exitValue() != 0) {
            String sortie = Files.exists(journal) ? Files.readString(journal) : "";
            log.warn("ffmpeg a échoué ({}) : {}", pr.exitValue(), sortie);
            String fin = sortie.length() > 300 ? sortie.substring(sortie.length() - 300) : sortie;
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Le montage a échoué : " + fin.trim()
                    + (sortie.contains("Invalid data") || sortie.contains("could not find codec") ? " (fichier de musique illisible ?)" : ""));
        }
    }

    /** Visuel centré sur un fond 9:16 fait du même visuel, flouté et assombri. */
    static BufferedImage trame(BufferedImage source) {
        BufferedImage t = new BufferedImage(LARGEUR, HAUTEUR, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = t.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        // Fond : l'image réduite à 1/20 puis agrandie, ce qui donne un flou doux sans calcul coûteux.
        double couvrir = Math.max((double) LARGEUR / source.getWidth(), (double) HAUTEUR / source.getHeight());
        int lf = (int) Math.ceil(source.getWidth() * couvrir), hf = (int) Math.ceil(source.getHeight() * couvrir);
        BufferedImage petit = new BufferedImage(Math.max(1, lf / 20), Math.max(1, hf / 20), BufferedImage.TYPE_INT_RGB);
        Graphics2D gp = petit.createGraphics();
        gp.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        gp.drawImage(source, 0, 0, petit.getWidth(), petit.getHeight(), null);
        gp.dispose();
        BufferedImage moyen = new BufferedImage(Math.max(1, lf / 5), Math.max(1, hf / 5), BufferedImage.TYPE_INT_RGB);
        Graphics2D gm = moyen.createGraphics();
        gm.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        gm.drawImage(petit, 0, 0, moyen.getWidth(), moyen.getHeight(), null);
        gm.dispose();
        g.drawImage(moyen, (LARGEUR - lf) / 2, (HAUTEUR - hf) / 2, lf, hf, null);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, LARGEUR, HAUTEUR);
        g.setComposite(AlphaComposite.SrcOver);
        // Premier plan : le visuel entier, sur toute la largeur (ou la hauteur pour une image très verticale).
        double ajuster = Math.min((double) LARGEUR / source.getWidth(), (double) HAUTEUR / source.getHeight());
        int l = (int) Math.round(source.getWidth() * ajuster), h = (int) Math.round(source.getHeight() * ajuster);
        g.drawImage(source, (LARGEUR - l) / 2, (HAUTEUR - h) / 2, l, h, null);
        g.dispose();
        return t;
    }

    private static void ecrireJpeg(BufferedImage image, Path chemin) throws IOException {
        ImageWriter w = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = w.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.92f);
        try (ImageOutputStream out = ImageIO.createImageOutputStream(chemin.toFile())) {
            w.setOutput(out);
            w.write(null, new IIOImage(image, null, null), param);
        } finally {
            w.dispose();
        }
    }

    private static String extension(String nom) {
        if (nom == null) return ".mp3";
        String n = nom.toLowerCase(Locale.ROOT);
        for (String e : List.of(".mp3", ".m4a", ".aac", ".wav", ".ogg")) if (n.endsWith(e)) return e;
        return ".mp3";
    }

    private static void supprimerDossier(Path dossier) {
        if (dossier == null) return;
        try (var fichiers = Files.walk(dossier)) {
            fichiers.sorted(Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
        } catch (IOException ignore) {
            // dossier temporaire : tant pis s'il reste
        }
    }
}
