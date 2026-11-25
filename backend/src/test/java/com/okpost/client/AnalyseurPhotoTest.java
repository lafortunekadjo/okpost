package com.okpost.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyseurPhotoTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void laDescriptionDUnProduitSExtraitDuJson() throws Exception {
        assertThat(AnalyseurPhoto.extraireTexte(json, "{\"texte\":\" Soutien-gorge en dentelle noire. \"}")).isEqualTo("Soutien-gorge en dentelle noire.");
        assertThat(AnalyseurPhoto.extraireTexte(json, "```json\n{\"texte\":\"Ensemble en coton rose.\"}\n```")).isEqualTo("Ensemble en coton rose.");
        assertThatThrownBy(() -> AnalyseurPhoto.extraireTexte(json, "{\"texte\":\"  \"}")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> AnalyseurPhoto.extraireTexte(json, "{}")).isInstanceOf(IOException.class);
    }

    @Test
    void laReponseDuModeleSeLitAvecOuSansBlocDeCode() throws Exception {
        var r = AnalyseurPhoto.parser(json,
                "```json\n{\"description\":\"Deux joueurs\",\"tags\":[\" Football \",\"football\",\"TEAM\"],\"utilisable\":false}\n```");
        assertThat(r.description()).isEqualTo("Deux joueurs");
        assertThat(r.tags()).containsExactly("football", "team");
        assertThat(r.utilisable()).isFalse();
    }

    @Test
    void uneReponseSansUtilisableEstConsidereeUtilisable() throws Exception {
        assertThat(AnalyseurPhoto.parser(json, "{\"description\":\"Un marché\",\"tags\":[\"marché\"]}").utilisable()).isTrue();
    }

    @Test
    void uneReponseVideEstRefusee() {
        assertThatThrownBy(() -> AnalyseurPhoto.parser(json, "{\"description\":\"\",\"tags\":[]}")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> AnalyseurPhoto.parser(json, "pas du json")).isInstanceOf(IOException.class);
    }

    @Test
    void laPhotoEstReduiteAvantEnvoi() throws Exception {
        BufferedImage grande = new BufferedImage(3000, 2000, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(grande, "png", o);
        BufferedImage petite = ImageIO.read(new ByteArrayInputStream(AnalyseurPhoto.reduire(o.toByteArray())));
        assertThat(petite.getWidth()).isEqualTo(1024);
        assertThat(petite.getHeight()).isEqualTo(683);
    }
}
