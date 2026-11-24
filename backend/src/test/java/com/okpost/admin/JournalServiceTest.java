package com.okpost.admin;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JournalServiceTest {

    private final JournalActiviteRepository depot = mock(JournalActiviteRepository.class);
    private final JournalService journal = new JournalService(depot, mock(PlatformTransactionManager.class));

    @Test
    void uneLigneEstEnregistreeAvecSesChampsNettoyes() {
        UUID espace = UUID.randomUUID();
        journal.enregistrer(espace, null, "  OkPost\n(automatique) ", Actions.PUBLICATION_PUBLIEE, "x".repeat(300), null);
        ArgumentCaptor<JournalActivite> c = ArgumentCaptor.forClass(JournalActivite.class);
        verify(depot).save(c.capture());
        assertThat(c.getValue().getWorkspaceId()).isEqualTo(espace);
        assertThat(c.getValue().getAuteur()).isEqualTo("OkPost (automatique)");
        assertThat(c.getValue().getCible()).hasSize(190);
        assertThat(c.getValue().getDetail()).isNull();
        assertThat(c.getValue().getAction()).isEqualTo("PUBLICATION_PUBLIEE");
    }

    @Test
    void uneEcritureEnEchecNeFaitJamaisEchouerLActionJournalisee() {
        when(depot.save(any())).thenThrow(new IllegalStateException("base indisponible"));
        assertThatCode(() -> journal.enregistrer(UUID.randomUUID(), UUID.randomUUID(), null, Actions.CONNEXION, "a@b.cm", null))
                .doesNotThrowAnyException();
    }

    @Test
    void touteActionAUnLibelle() {
        for (String a : new String[]{Actions.INSCRIPTION, Actions.CONNEXION, Actions.CONNEXION_ECHEC, Actions.MEMBRE_INVITE,
                Actions.CLIENT_CREE, Actions.CLIENT_ARCHIVE, Actions.CALENDRIER_CREE, Actions.LIEN_VALIDATION,
                Actions.VALIDATION_CLIENT, Actions.PUBLICATION_PUBLIEE, Actions.PUBLICATION_ECHEC}) {
            assertThat(Actions.LIBELLES).containsKey(a);
        }
    }

    @Test
    void lesTextesVidesOuBlancsDonnentNull() {
        assertThat(JournalService.coupe("   ", 10)).isNull();
        assertThat(JournalService.coupe(null, 10)).isNull();
        assertThat(JournalService.coupe(" abc ", 10)).isEqualTo("abc");
    }
}
