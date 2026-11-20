package com.vms.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.vms.config.JwtConfig;
import com.vms.config.JwtProperties;
import com.vms.entity.AppUser;
import com.vms.entity.UserRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class JwtTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final JwtProperties PROPERTIES = new JwtProperties(
            "unit-test-signing-key-0123456789-abcdefghijklmnop", Duration.ofMinutes(15), "vms-test");

    private final JwtConfig config = new JwtConfig();
    private final JwtTokenService service = new JwtTokenService(
            config.jwtEncoder(PROPERTIES), PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));
    private final AppUser admin = new AppUser("admin@example.com", "{bcrypt}hash", UserRole.ADMIN);

    @Test
    void tokenCarriesIssuerSubjectRolesAndExpiry() {
        JwtDecoder decoder = lenientDecoder();

        Jwt jwt = decoder.decode(service.issue(admin).value());

        assertThat(jwt.getClaimAsString("iss")).isEqualTo("vms-test");
        assertThat(jwt.getSubject()).isEqualTo("admin@example.com");
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
    }

    @Test
    void reportsTheConfiguredLifetime() {
        assertThat(service.issue(admin).ttl()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void tokenNeverContainsThePasswordHash() {
        String token = service.issue(admin).value();

        assertThat(token).doesNotContain("bcrypt");
        assertThat(new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1])))
                .doesNotContain("hash").doesNotContain("password");
    }

    @Test
    void toStringsDoNotLeakSecrets() {
        assertThat(PROPERTIES.toString()).doesNotContain("unit-test-signing-key");
        assertThat(service.issue(admin).toString()).doesNotContain("ey");
    }

    // The fixed clock is in the past, so skip the expiry check; signature and issuer are still verified.
    private JwtDecoder lenientDecoder() {
        org.springframework.security.oauth2.jwt.NimbusJwtDecoder decoder =
                org.springframework.security.oauth2.jwt.NimbusJwtDecoder
                        .withSecretKey(new javax.crypto.spec.SecretKeySpec(
                                PROPERTIES.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"))
                        .macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();
        decoder.setJwtValidator(jwt -> org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success());
        return decoder;
    }
}
