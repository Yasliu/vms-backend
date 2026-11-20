package com.vms.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vms.entity.AppUser;
import com.vms.entity.UserRole;
import com.vms.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class AdminBootstrapTest {

    private static final String PASSWORD = "a-long-bootstrap-pass-phrase";

    @Mock
    AppUserRepository users;

    PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private AdminBootstrap bootstrap(String email, String password) {
        return new AdminBootstrap(users, encoder, email, password);
    }

    @Test
    void createsTheAdminWithAHashedPasswordWhenNoUsersExist(CapturedOutput output) {
        when(users.count()).thenReturn(0L);

        bootstrap(" Admin@Example.com ", PASSWORD).run(null);

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.com");
        assertThat(saved.getValue().getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(saved.getValue().getPasswordHash()).startsWith("{bcrypt}").doesNotContain(PASSWORD);
        assertThat(encoder.matches(PASSWORD, saved.getValue().getPasswordHash())).isTrue();
        assertThat(output.getAll()).doesNotContain(PASSWORD);
    }

    @Test
    void doesNothingOnceAUserExists() {
        when(users.count()).thenReturn(1L);

        bootstrap("admin@example.com", PASSWORD).run(null);

        verify(users, never()).save(any());
    }

    @Test
    void withoutCredentialsItWarnsAndStartsAnyway(CapturedOutput output) {
        when(users.count()).thenReturn(0L);

        bootstrap("", "").run(null);

        verify(users, never()).save(any());
        assertThat(output.getAll()).contains("nobody can sign in");
    }

    @Test
    void weakPasswordsAreRefusedWithoutEchoingThem(CapturedOutput output) {
        when(users.count()).thenReturn(0L);

        assertThatThrownBy(() -> bootstrap("admin@example.com", "short").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_PASSWORD")
                .hasMessageNotContaining("short ");
        verify(users, never()).save(any());
        assertThat(output.getAll()).doesNotContain("short");
    }

    @Test
    void invalidEmailIsRefused() {
        when(users.count()).thenReturn(0L);

        assertThatThrownBy(() -> bootstrap("not-an-email", PASSWORD).run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ADMIN_EMAIL");
    }
}
