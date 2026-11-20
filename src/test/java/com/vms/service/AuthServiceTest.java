package com.vms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vms.dto.LoginResponse;
import com.vms.entity.AppUser;
import com.vms.entity.UserRole;
import com.vms.exception.InvalidCredentialsException;
import com.vms.repository.AppUserRepository;
import com.vms.security.JwtTokenService;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "correct horse battery staple";

    @Mock
    AppUserRepository users;

    @Mock
    JwtTokenService tokens;

    PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    AuthService service;
    AppUser admin;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, encoder, tokens);
        admin = new AppUser("admin@example.com", encoder.encode(PASSWORD), UserRole.ADMIN);
    }

    @Test
    void validCredentialsReturnABearerToken() {
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(tokens.issue(admin)).thenReturn(new JwtTokenService.IssuedToken("the.jwt.token", Duration.ofMinutes(30)));

        LoginResponse response = service.login("admin@example.com", PASSWORD);

        assertThat(response.accessToken()).isEqualTo("the.jwt.token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(1800);
        assertThat(response.email()).isEqualTo("admin@example.com");
    }

    @Test
    void emailIsTrimmedAndCaseInsensitive() {
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(tokens.issue(admin)).thenReturn(new JwtTokenService.IssuedToken("t", Duration.ofMinutes(1)));

        service.login("  Admin@Example.COM ", PASSWORD);

        verify(users).findByEmail("admin@example.com");
    }

    @Test
    void wrongPasswordIsRejected() {
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.login("admin@example.com", "wrong password"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");
    }

    @Test
    void unknownEmailIsRejectedWithTheSameError() {
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("nobody@example.com", PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");
    }

    @Test
    void unknownEmailStillPaysForAPasswordHashCheck() {
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        long unknown = timeOf(() -> service.login("nobody@example.com", PASSWORD));
        long wrongPassword = timeOf(() -> service.login("admin@example.com", "wrong password"));

        // Both paths run a BCrypt comparison (tens of milliseconds), so neither is near-instant.
        assertThat(unknown).isGreaterThan(Duration.ofMillis(10).toNanos());
        assertThat(wrongPassword).isGreaterThan(Duration.ofMillis(10).toNanos());
    }

    @Test
    void overlongPasswordsAreRejectedWithoutError() {
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.login("admin@example.com", "x".repeat(AuthService.MAX_PASSWORD_BYTES + 1)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void storedHashIsNotThePlainPassword() {
        assertThat(admin.getPasswordHash()).startsWith("{bcrypt}").doesNotContain(PASSWORD);
        assertThat(admin.toString()).doesNotContain(admin.getPasswordHash());
    }

    private static long timeOf(Runnable action) {
        long start = System.nanoTime();
        try {
            action.run();
        } catch (InvalidCredentialsException expected) {
            // timing only
        }
        return System.nanoTime() - start;
    }
}
