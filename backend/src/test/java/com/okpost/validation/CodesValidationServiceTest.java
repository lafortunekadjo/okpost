package com.okpost.validation;

import com.okpost.client.ClientService;
import com.okpost.commun.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodesValidationServiceTest {

    private final BCryptPasswordEncoder encodeur = new BCryptPasswordEncoder(4);
    private final ContactValidationRepository depot = mock(ContactValidationRepository.class);
    private final CodesValidationService service = new CodesValidationService(depot, mock(ClientService.class), encodeur);
    private final UUID client = UUID.randomUUID();

    private ContactValidation contact(String nom, String code) {
        ContactValidation v = new ContactValidation();
        v.setId(UUID.randomUUID());
        v.setNom(nom);
        v.setClientId(client);
        v.setCodeHash(encodeur.encode(code));
        return v;
    }

    @Test
    void sansPersonneAutoriseeLeLienSecretSuffit() {
        when(depot.findByClientIdAndActifTrue(client)).thenReturn(List.of());
        assertThat(service.codeRequis(client)).isFalse();
        assertThat(service.verifier("jeton", client, null)).isEmpty();
    }

    @Test
    void chaqueCodeIdentifieSaPersonne() {
        ContactValidation marie = contact("Marie", "123456");
        ContactValidation paul = contact("Paul", "654321");
        when(depot.findByClientIdAndActifTrue(client)).thenReturn(List.of(marie, paul));
        assertThat(service.codeRequis(client)).isTrue();
        assertThat(service.verifier("j1", client, "654321")).get().extracting(CodesValidationService.Validateur::nom).isEqualTo("Paul");
        assertThat(service.verifier("j1", client, " 123456 ")).get().extracting(CodesValidationService.Validateur::nom).isEqualTo("Marie");
        assertThat(marie.getDerniereUtilisation()).isNotNull();
    }

    @Test
    void unCodeFauxOuAbsentEstRefuse() {
        when(depot.findByClientIdAndActifTrue(client)).thenReturn(List.of(contact("Marie", "123456")));
        for (String faux : new String[]{"000000", "12345", "abcdef", "", null}) {
            assertThatThrownBy(() -> service.verifier("j2", client, faux)).isInstanceOf(ApiException.class).hasMessageContaining("Code incorrect");
        }
    }

    @Test
    void ApresCinqEchecsLeLienEstBloqueMemeAvecLeBonCode() {
        when(depot.findByClientIdAndActifTrue(client)).thenReturn(List.of(contact("Marie", "123456")));
        for (int i = 0; i < CodesValidationService.ESSAIS_MAX; i++) {
            assertThatThrownBy(() -> service.verifier("j3", client, "000000")).isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(() -> service.verifier("j3", client, "123456"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatut()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        // Un autre lien n'est pas touché
        assertThat(service.verifier("autre-lien", client, "123456")).isPresent();
    }

    @Test
    void unBonCodeRemetLesEchecsAZero() {
        when(depot.findByClientIdAndActifTrue(client)).thenReturn(List.of(contact("Marie", "123456")));
        for (int i = 0; i < CodesValidationService.ESSAIS_MAX - 1; i++) {
            assertThatThrownBy(() -> service.verifier("j4", client, "000000")).isInstanceOf(ApiException.class);
        }
        assertThat(service.verifier("j4", client, "123456")).isPresent();
        for (int i = 0; i < CodesValidationService.ESSAIS_MAX - 1; i++) {
            assertThatThrownBy(() -> service.verifier("j4", client, "000000")).isInstanceOf(ApiException.class);
        }
        assertThat(service.verifier("j4", client, "123456")).isPresent();
    }
}
