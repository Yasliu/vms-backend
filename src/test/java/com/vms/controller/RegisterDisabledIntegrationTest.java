package com.vms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vms.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** With no REGISTRATION_CODE configured, sign-up must be off no matter what is sent. */
@SpringBootTest(properties = "app.security.registration-code=")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RegisterDisabledIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AppUserRepository users;

    @Test
    void signUpIsOffWhenNoCodeIsConfigured() throws Exception {
        for (String code : new String[] {"", "anything-at-all-123", "test-only-registration-code-12345"}) {
            mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"new@example.com\",\"password\":\"a-long-unique-password-1\",\"registrationCode\":\"%s\"}"
                            .formatted(code.isEmpty() ? "x".repeat(12) : code)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail", is("Sign-up is not available right now.")));
        }
        assertThat(users.count()).isZero();
    }
}
