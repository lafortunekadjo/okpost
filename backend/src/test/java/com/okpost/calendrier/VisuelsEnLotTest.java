package com.okpost.calendrier;

import com.okpost.calendrier.VisuelsEnLotService.Progression;
import com.okpost.commun.ApiException;
import com.okpost.ia.Modeles.BriefVisuel;
import com.okpost.securite.Role;
import com.okpost.securite.Session;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisuelsEnLotTest {

    private final UUID espace = UUID.randomUUID();
    private final UUID calendrierId = UUID.randomUUID();
    private final Session session = new Session(UUID.randomUUID(), espace, Role.ADMIN);

    private final CalendrierService calendriers = mock(CalendrierService.class);
    private final PublicationRepository publications = mock(PublicationRepository.class);
    private final VisuelGenereRepository visuels = mock(VisuelGenereRepository.class);
    private final VisuelService generateur = mock(VisuelService.class);
    private final VisuelsEnLotService service = new VisuelsEnLotService(calendriers, publications, visuels, generateur);

    private static final BriefVisuel BRIEF = new BriefVisuel("IMAGE", "d", "A photo of a shop", "1:1", null, "Titre", null, null, null, null, null, null);

    private Publication pub(String format, StatutPublication statut, boolean detail, BriefVisuel brief, int jour) {
        Publication p = new Publication();
        p.setId(UUID.randomUUID());
        p.setCalendrierId(calendrierId);
        p.setDatePrevue(LocalDate.of(2026, 12, jour));
        p.setReseau("FACEBOOK");
        p.setFormat(format);
        p.setStatut(statut);
        p.setDetailGenere(detail);
        p.setBrief(brief);
        return p;
    }

    private Progression attendreFin() throws Exception {
        for (int i = 0; i < 100; i++) {
            Progression p = service.etat(session, calendrierId).orElseThrow();
            if ("TERMINE".equals(p.etat())) return p;
            Thread.sleep(30);
        }
        throw new AssertionError("la génération ne se termine pas");
    }

    private void reponses(List<Publication> pubs, List<Publication> avecVisuel) {
        when(publications.findByCalendrierIdOrderByDatePrevueAscReseauAsc(calendrierId)).thenReturn(pubs);
        List<VisuelGenere> deja = avecVisuel.stream().map(p -> {
            VisuelGenere v = new VisuelGenere();
            v.setPublicationId(p.getId());
            v.setTypeMedia(VisuelGenere.TypeMedia.IMAGE);
            return v;
        }).toList();
        when(visuels.findByPublicationIdInOrderByCreatedAtDesc(any())).thenReturn(deja);
    }

    @Test
    void seulesLesPublicationsIllustrablesEtSansVisuelSontGenerees() throws Exception {
        Publication a = pub("IMAGE", StatutPublication.A_VALIDER, true, BRIEF, 1);
        Publication b = pub("STORY", StatutPublication.BROUILLON, true, BRIEF, 2);
        Publication dejaFaite = pub("IMAGE", StatutPublication.A_VALIDER, true, BRIEF, 3);
        List<Publication> toutes = List.of(a, b, dejaFaite,
                pub("VIDEO", StatutPublication.A_VALIDER, true, BRIEF, 4),
                pub("CARROUSEL", StatutPublication.A_VALIDER, true, BRIEF, 5),
                pub("IMAGE", StatutPublication.PROGRAMMEE, true, BRIEF, 6),
                pub("IMAGE", StatutPublication.IDEE, false, BRIEF, 7),
                pub("IMAGE", StatutPublication.A_VALIDER, true, null, 8));
        reponses(toutes, List.of(dejaFaite));

        Progression depart = service.demarrer(session, calendrierId, false, false);
        assertThat(depart.total()).isEqualTo(2);
        assertThat(depart.ignorees()).hasSize(6);
        assertThat(depart.ignorees()).anyMatch(l -> l.contains("a déjà un visuel")).anyMatch(l -> l.contains("vidéo"))
                .anyMatch(l -> l.contains("carrousel")).anyMatch(l -> l.contains("programmée"))
                .anyMatch(l -> l.contains("pas encore rédigé")).anyMatch(l -> l.contains("pas de description d'image"));

        Progression fin = attendreFin();
        assertThat(fin.faits()).isEqualTo(2);
        assertThat(fin.echecs()).isZero();
        assertThat(fin.fin()).isNotNull();
        verify(generateur).generer(eq(session), eq(a.getId()), any(), any(), eq(true), any(), any(), any());
        verify(generateur).generer(eq(session), eq(b.getId()), any(), any(), eq(true), any(), any(), any());
    }

    @Test
    void remplacerRefaitAussiLesPublicationsQuiOntDejaUnVisuel() throws Exception {
        Publication dejaFaite = pub("IMAGE", StatutPublication.A_VALIDER, true, BRIEF, 3);
        reponses(List.of(dejaFaite), List.of(dejaFaite));
        assertThat(service.demarrer(session, calendrierId, true, true).total()).isEqualTo(1);
        attendreFin();
        verify(generateur).generer(eq(session), eq(dejaFaite.getId()), any(), any(), anyBoolean(),
                eq(new VisuelService.Saisie(null, null, null, null, null, null, Boolean.TRUE)), any(), any());
    }

    @Test
    void leQuotaAtteintArreteToutLeLot() throws Exception {
        Publication a = pub("IMAGE", StatutPublication.A_VALIDER, true, BRIEF, 1);
        Publication b = pub("IMAGE", StatutPublication.A_VALIDER, true, BRIEF, 2);
        reponses(List.of(a, b), List.of());
        when(generateur.generer(any(), eq(a.getId()), any(), any(), anyBoolean(), any(), any(), any()))
                .thenThrow(new ApiException(HttpStatus.PAYMENT_REQUIRED, "Tu as utilisé tes 5 images IA du mois"));
        service.demarrer(session, calendrierId, false, false);
        Progression fin = attendreFin();
        assertThat(fin.faits()).isZero();
        assertThat(fin.erreurs()).anyMatch(e -> e.contains("images IA du mois")).anyMatch(e -> e.contains("arrêtée"));
        verify(generateur, times(1)).generer(any(), any(), any(), any(), anyBoolean(), any(), any(), any());
    }

    @Test
    void uneErreurSurUnePublicationNArretePasLesSuivantes() throws Exception {
        Publication a = pub("IMAGE", StatutPublication.A_VALIDER, true, BRIEF, 1);
        Publication b = pub("IMAGE", StatutPublication.A_VALIDER, true, BRIEF, 2);
        reponses(List.of(a, b), List.of());
        when(generateur.generer(any(), eq(a.getId()), any(), any(), anyBoolean(), any(), any(), any()))
                .thenThrow(ApiException.requeteInvalide("Aucune photo pour « Body »"));
        service.demarrer(session, calendrierId, false, false);
        Progression fin = attendreFin();
        assertThat(fin.faits()).isEqualTo(1);
        assertThat(fin.echecs()).isEqualTo(1);
        assertThat(fin.erreurs().get(0)).contains("Aucune photo pour « Body »").contains("2026-12-01");
    }

    @Test
    void unDeuxiemeLancementPendantLeLotEstRefuse() throws Exception {
        Publication a = pub("IMAGE", StatutPublication.A_VALIDER, true, BRIEF, 1);
        reponses(List.of(a), List.of());
        CountDownLatch bloque = new CountDownLatch(1);
        when(generateur.generer(any(), any(), any(), any(), anyBoolean(), any(), any(), any())).thenAnswer(i -> {
            bloque.await(5, TimeUnit.SECONDS);
            return null;
        });
        service.demarrer(session, calendrierId, false, false);
        assertThatThrownBy(() -> service.demarrer(session, calendrierId, false, false))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatut()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(service.etat(session, calendrierId).orElseThrow().etat()).isEqualTo("EN_COURS");
        bloque.countDown();
        assertThat(attendreFin().faits()).isEqualTo(1);
        // Une fois terminé, on peut relancer
        service.demarrer(session, calendrierId, false, false);
        attendreFin();
    }

    @Test
    void sansPublicationAGenererLeLotSeTermineToutDeSuite() {
        reponses(List.of(pub("VIDEO", StatutPublication.A_VALIDER, true, BRIEF, 1)), List.of());
        Progression p = service.demarrer(session, calendrierId, false, false);
        assertThat(p.etat()).isEqualTo("TERMINE");
        assertThat(p.total()).isZero();
        assertThat(p.ignorees()).hasSize(1);
        assertThat(service.etat(session, UUID.randomUUID())).isEmpty();
    }
}
