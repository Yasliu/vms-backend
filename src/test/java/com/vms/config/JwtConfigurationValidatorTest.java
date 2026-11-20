package com.vms.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class JwtConfigurationValidatorTest {

    private static MockEnvironment env() {
        return new MockEnvironment().withProperty("app.security.jwt.secret", "${JWT_SECRET}");
    }

    @Test
    void unresolvedPlaceholderIsReportedByVariableName() {
        assertThat(JwtConfigurationValidator.problemWith(env())).contains("JWT_SECRET").contains("Missing");
    }

    @Test
    void blankSecretIsRejected() {
        assertThat(JwtConfigurationValidator.problemWith(env().withProperty("JWT_SECRET", "  "))).contains("Missing");
    }

    @Test
    void shortSecretIsRejectedWithoutRevealingIt() {
        String problem = JwtConfigurationValidator.problemWith(env().withProperty("JWT_SECRET", "too-short-secret"));

        assertThat(problem).contains("too short").contains("32");
        assertThat(problem).doesNotContain("too-short-secret");
    }

    @Test
    void strongSecretIsAccepted() {
        assertThat(JwtConfigurationValidator.problemWith(
                env().withProperty("JWT_SECRET", "x".repeat(JwtConfigurationValidator.MIN_SECRET_BYTES)))).isNull();
    }
}
