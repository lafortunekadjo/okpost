package com.okpost.calendrier;

import com.okpost.commun.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MusiqueServiceTest {

    private static byte[] mp3() {
        byte[] o = new byte[2048];
        System.arraycopy("ID3".getBytes(StandardCharsets.ISO_8859_1), 0, o, 0, 3);
        return o;
    }

    private static MockMultipartFile fichier(String nom, byte[] contenu) {
        return new MockMultipartFile("fichier", nom, "audio/mpeg", contenu);
    }

    @Test
    void ajouteListeEtSupprime(@TempDir Path dossier) {
        MusiqueService s = new MusiqueService(dossier.toString());
        assertThat(s.lister()).isEmpty();

        var piste = s.ajouter(fichier("Afro_chill-doux.mp3", mp3()));
        assertThat(piste.nom()).isEqualTo("Afro chill-doux");
        assertThat(s.lister()).extracting(MusiqueService.Piste::id).containsExactly(piste.id());
        assertThat(s.resoudre(piste.id()).chemin()).exists();

        s.supprimer(piste.id());
        assertThat(s.lister()).isEmpty();
    }

    @Test
    void deuxFichiersDeMemeNomNeSEcrasentPas(@TempDir Path dossier) {
        MusiqueService s = new MusiqueService(dossier.toString());
        s.ajouter(fichier("piste.mp3", mp3()));
        s.ajouter(fichier("piste.mp3", mp3()));
        assertThat(s.lister()).hasSize(2);
    }

    @Test
    void unNomPiegeNeSortPasDuDossier(@TempDir Path dossier) throws Exception {
        Path sousDossier = Files.createDirectory(dossier.resolve("musiques"));
        MusiqueService s = new MusiqueService(sousDossier.toString());
        var piste = s.ajouter(fichier("../../evil.mp3", mp3()));
        assertThat(piste.chemin().getParent()).isEqualTo(sousDossier);
        try (var tous = Files.list(dossier)) {
            assertThat(tous.map(p -> p.getFileName().toString()).toList()).containsExactly("musiques");
        }
    }

    @Test
    void refuseLesFormatsEtLesFauxFichiers(@TempDir Path dossier) {
        MusiqueService s = new MusiqueService(dossier.toString());
        assertThatThrownBy(() -> s.ajouter(fichier("script.exe", mp3()))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> s.ajouter(fichier("faux.mp3", "ceci n'est pas de l'audio du tout".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(ApiException.class).hasMessageContaining("audio");
        assertThatThrownBy(() -> s.ajouter(fichier("vide.mp3", new byte[0]))).isInstanceOf(ApiException.class);
        assertThat(s.lister()).isEmpty();
    }

    @Test
    void refuseUnFichierTropLourd(@TempDir Path dossier) {
        MusiqueService s = new MusiqueService(dossier.toString());
        byte[] gros = new byte[(int) MusiqueService.MAX + 1];
        System.arraycopy(mp3(), 0, gros, 0, 3);
        assertThatThrownBy(() -> s.ajouter(fichier("gros.mp3", gros))).isInstanceOf(ApiException.class).hasMessageContaining("lourde");
    }

    @Test
    void uneMusiqueInconnueEstIntrouvable(@TempDir Path dossier) {
        MusiqueService s = new MusiqueService(dossier.toString());
        assertThatThrownBy(() -> s.resoudre("../../etc/passwd")).isInstanceOf(ApiException.class);
    }

    @Test
    void reconnaitLesEntetesAudio() {
        assertThat(MusiqueService.ressembleAUnAudio(mp3())).isTrue();
        byte[] ogg = Arrays.copyOf("OggS".getBytes(StandardCharsets.ISO_8859_1), 32);
        assertThat(MusiqueService.ressembleAUnAudio(ogg)).isTrue();
        byte[] adts = new byte[32];
        adts[0] = (byte) 0xFF;
        adts[1] = (byte) 0xF1;
        assertThat(MusiqueService.ressembleAUnAudio(adts)).isTrue();
        assertThat(MusiqueService.ressembleAUnAudio(new byte[32])).isFalse();
        assertThat(MusiqueService.typeMime("a.M4A")).isEqualTo("audio/mp4");
    }
}
