package com.okpost.reseaux;

import com.okpost.reseaux.ErreursMeta.Nature;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ErreursMetaTest {

    @Test
    void unJetonOuUnePermissionPerdueDemandeUneReconnexion() {
        assertThat(ErreursMeta.classer("Facebook : Error validating access token: Session has expired (#190)")).isEqualTo(Nature.ACCES);
        assertThat(ErreursMeta.classer("Instagram : Application does not have permission for this action")).isEqualTo(Nature.ACCES);
        assertThat(ErreursMeta.classer("Facebook : (#200) Requires pages_manage_posts permission")).isEqualTo(Nature.ACCES);
    }

    @Test
    void unMediaRefuseNEstPasReessaye() {
        assertThat(ErreursMeta.classer("Instagram a refusé le média : ERROR")).isEqualTo(Nature.DEFINITIVE);
        assertThat(ErreursMeta.classer("Instagram : The aspect ratio is not supported")).isEqualTo(Nature.DEFINITIVE);
        assertThat(ErreursMeta.classer("Carrousel en vidéo défilante choisi, mais la vidéo n'est pas encore montée")).isEqualTo(Nature.DEFINITIVE);
        assertThat(ErreursMeta.classer("Instagram n'accepte que le JPEG : cette image ne peut pas être convertie")).isEqualTo(Nature.DEFINITIVE);
    }

    @Test
    void lesPannesPassageresSontReessayees() {
        assertThat(ErreursMeta.classer("Facebook : erreur 503")).isEqualTo(Nature.TEMPORAIRE);
        assertThat(ErreursMeta.classer("Instagram met trop de temps à traiter le média (nouvel essai plus tard)")).isEqualTo(Nature.TEMPORAIRE);
        assertThat(ErreursMeta.classer("Facebook : (#4) Application request limit reached")).isEqualTo(Nature.TEMPORAIRE);
        assertThat(ErreursMeta.classer("I/O error on POST request")).isEqualTo(Nature.TEMPORAIRE);
    }

    @Test
    void uneErreurInconnueEstReessayeeUneFoisDePlus() {
        assertThat(ErreursMeta.classer("quelque chose d'inattendu")).isEqualTo(Nature.TEMPORAIRE);
        assertThat(ErreursMeta.classer(null)).isEqualTo(Nature.TEMPORAIRE);
    }

    @Test
    void lAttenteAugmenteEntreLesEssais() {
        assertThat(ErreursMeta.delai(1)).isEqualTo(Duration.ofMinutes(5));
        assertThat(ErreursMeta.delai(2)).isEqualTo(Duration.ofMinutes(20));
        assertThat(ErreursMeta.delai(3)).isEqualTo(Duration.ofHours(1));
    }
}
