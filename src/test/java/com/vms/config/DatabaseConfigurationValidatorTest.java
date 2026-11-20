package com.vms.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DatabaseConfigurationValidatorTest {

    private static MockEnvironment baseEnvironment() {
        return new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME}")
                .withProperty("spring.datasource.username", "${DB_USERNAME}")
                .withProperty("spring.datasource.password", "${DB_PASSWORD}");
    }

    @Test
    void reportsEveryMissingVariableByName() {
        assertThat(DatabaseConfigurationValidator.missingVariables(baseEnvironment()))
                .containsExactly("DB_NAME", "DB_USERNAME", "DB_PASSWORD");
    }

    @Test
    void passesWhenAllRequiredVariablesAreSet() {
        MockEnvironment env = baseEnvironment()
                .withProperty("DB_NAME", "vms")
                .withProperty("DB_USERNAME", "app")
                .withProperty("DB_PASSWORD", "secret");

        assertThat(DatabaseConfigurationValidator.missingVariables(env)).isEmpty();
    }

    @Test
    void optionalHostAndPortFallBackToDefaults() {
        MockEnvironment env = baseEnvironment()
                .withProperty("DB_NAME", "vms")
                .withProperty("DB_USERNAME", "app")
                .withProperty("DB_PASSWORD", "secret");

        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://localhost:5432/vms");
    }
}
