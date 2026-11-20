package com.vms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vms.entity.AppUser;
import com.vms.entity.UserRole;
import com.vms.repository.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = {
        // Turned up on purpose: even verbose logging must never contain a password or token.
        "logging.level.org.springframework.web=DEBUG",
        "logging.level.org.springframework.security=DEBUG",
        "logging.level.com.vms=DEBUG"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AuthApiIntegrationTest {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String PASSWORD = "Tr0ub4dor&3-unique-marker-xyz";

    @Autowired
    MockMvc mvc;

    @Autowired
    AppUserRepository users;

    @Autowired
    PasswordEncoder encoder;

    @BeforeEach
    void createUser() {
        users.deleteAll();
        users.save(new AppUser("admin@example.com", encoder.encode(PASSWORD), UserRole.ADMIN));
    }

    @AfterEach
    void removeUsers() {
        users.deleteAll();
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    @Test
    void validCredentialsReturnABearerToken() throws Exception {
        login("admin@example.com", PASSWORD).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.expiresIn", is(1800)))
                .andExpect(jsonPath("$.email", is("admin@example.com")))
                .andExpect(jsonPath("$.*", hasSize(4)))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void emailMatchingIgnoresCaseAndSurroundingSpaces() throws Exception {
        login("  ADMIN@Example.com ", PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("admin@example.com")));
    }

    @Test
    void theIssuedTokenUnlocksProtectedOperations() throws Exception {
        String body = login("admin@example.com", PASSWORD).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        mvc.perform(get("/api/v1/vehicles")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/vehicles").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordReturns401WithAGenericMessage() throws Exception {
        login("admin@example.com", "not the password").andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.title", is("Authentication failed")))
                .andExpect(jsonPath("$.detail", is("Invalid email or password.")))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void unknownEmailIsIndistinguishableFromAWrongPassword() throws Exception {
        String wrongPassword = login("admin@example.com", "nope").andReturn().getResponse().getContentAsString();
        String unknownEmail = login("ghost@example.com", "nope").andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(unknownEmail, "$.detail"))
                .isEqualTo(JsonPath.<String>read(wrongPassword, "$.detail"));
        assertThat(JsonPath.<String>read(unknownEmail, "$.title"))
                .isEqualTo(JsonPath.<String>read(wrongPassword, "$.title"));
    }

    @Test
    void blankFieldsReturn400() throws Exception {
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)));
    }

    @Test
    void malformedBodyReturns400() throws Exception {
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content("{broken " + PASSWORD))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordsAreStoredOnlyAsHashes() {
        AppUser stored = users.findByEmail("admin@example.com").orElseThrow();

        assertThat(stored.getPasswordHash()).startsWith("{bcrypt}").doesNotContain(PASSWORD);
    }

    @Test
    void neitherPasswordsNorTokensAppearInLogsOnAnyPath(CapturedOutput output) throws Exception {
        String body = login("admin@example.com", PASSWORD).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");
        String wrongPassword = "Wr0ng-unique-marker-abc";
        login("admin@example.com", wrongPassword);
        login("ghost@example.com", wrongPassword);
        login("", wrongPassword);
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content("{broken " + wrongPassword));
        mvc.perform(get("/api/v1/vehicles").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        mvc.perform(get("/api/v1/vehicles").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token-marker"));

        assertThat(output.getAll())
                .doesNotContain(PASSWORD)
                .doesNotContain(wrongPassword)
                .doesNotContain(token)
                .doesNotContain("not-a-real-token-marker")
                .doesNotContain("Using generated security password");
    }

    @Test
    void getOnTheLoginUrlIsNotAnAuthenticationShortcut() throws Exception {
        mvc.perform(get(LOGIN + "?email=admin@example.com&password=" + PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.detail", not(containsString(PASSWORD))));
    }
}
