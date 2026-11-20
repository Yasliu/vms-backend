package com.vms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vms.dto.LoginResponse;
import com.vms.entity.AppUser;
import com.vms.entity.UserRole;
import com.vms.exception.DuplicateEmailException;
import com.vms.exception.InvalidRequestException;
import com.vms.exception.RegistrationNotAllowedException;
import com.vms.repository.AppUserRepository;
import com.vms.security.JwtTokenService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    private static final String CODE = "team-registration-code-2026";
    private static final String PASSWORD = "a-long-unique-password-42";

    @Mock
    AppUserRepository users;

    @Mock
    JwtTokenService tokens;

    PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private RegistrationService service(String code) {
        return new RegistrationService(users, encoder, tokens, code);
    }

    @Test
    void validRegistrationCreatesAnAdminWithAHashedPasswordAndReturnsAToken() {
        when(users.existsByEmail("new@example.com")).thenReturn(false);
        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokens.issue(any(AppUser.class))).thenReturn(new JwtTokenService.IssuedToken("the.jwt", Duration.ofMinutes(30)));

        LoginResponse response = service(CODE).register("  New@Example.com ", PASSWORD, CODE);

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("new@example.com");
        assertThat(saved.getValue().getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(saved.getValue().getPasswordHash()).startsWith("{bcrypt}").doesNotContain(PASSWORD);
        assertThat(encoder.matches(PASSWORD, saved.getValue().getPasswordHash())).isTrue();
        assertThat(response.accessToken()).isEqualTo("the.jwt");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(1800);
        assertThat(response.email()).isEqualTo("new@example.com");
    }

    @Test
    void aWrongCodeIsRefusedAndNothingIsSaved() {
        assertThatThrownBy(() -> service(CODE).register("new@example.com", PASSWORD, "not-the-code"))
                .isInstanceOf(RegistrationNotAllowedException.class)
                .hasMessage("That registration code is not valid.");

        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void theCodeIsCheckedBeforeEmailsSoOutsidersLearnNothingAboutExistingAccounts() {
        // No stubbing of existsByEmail: if the service looked it up first, strict stubs would not allow
        // it, and a wrong code for an existing email must look the same as for a new one.
        assertThatThrownBy(() -> service(CODE).register("taken@example.com", PASSWORD, "wrong-code-value"))
                .isInstanceOf(RegistrationNotAllowedException.class);

        verify(users, never()).existsByEmail(any());
    }

    @Test
    void withNoCodeConfiguredSignUpIsOffEvenForAnEmptyOrMatchingGuess() {
        for (String attempt : new String[] {"", " ", "anything-at-all-12"}) {
            assertThatThrownBy(() -> service("").register("new@example.com", PASSWORD, attempt))
                    .isInstanceOf(RegistrationNotAllowedException.class)
                    .hasMessage("Sign-up is not available right now.");
        }
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void anExistingEmailIsRejectedCaseInsensitively() {
        when(users.existsByEmail("admin@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service(CODE).register("ADMIN@example.com", PASSWORD, CODE))
                .isInstanceOf(DuplicateEmailException.class);

        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void aConcurrentSignUpWithTheSameEmailBecomesTheSameError() {
        when(users.existsByEmail("new@example.com")).thenReturn(false);
        when(users.saveAndFlush(any(AppUser.class))).thenThrow(new DataIntegrityViolationException("uk_app_users_email"));

        assertThatThrownBy(() -> service(CODE).register("new@example.com", PASSWORD, CODE))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void passwordsLongerThan72BytesAreRefused() {
        assertThatThrownBy(() -> service(CODE).register("new@example.com", "é".repeat(40), CODE))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("field").isEqualTo("password");
    }

    @Test
    void aTooShortCodeStopsStartupWithoutRevealingIt() {
        assertThatThrownBy(() -> service("short-code"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REGISTRATION_CODE")
                .hasMessageNotContaining("short-code");
    }

    @Test
    void noCodeAtAllIsAllowedAtStartup() {
        service("");
    }
}
