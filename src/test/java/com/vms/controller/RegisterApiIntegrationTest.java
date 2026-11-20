package com.vms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
        "logging.level.org.springframework.web=DEBUG",
        "logging.level.org.springframework.security=DEBUG",
        "logging.level.com.vms=DEBUG"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class RegisterApiIntegrationTest {

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String CODE = "test-only-registration-code-12345";
    private static final String PASSWORD = "Sign-up-pass-unique-marker-77";

    @Autowired
    MockMvc mvc;

    @Autowired
    AppUserRepository users;

    @Autowired
    PasswordEncoder encoder;

    @BeforeEach
    @AfterEach
    void clean() {
        users.deleteAll();
    }

    private ResultActions register(String email, String password, String code) throws Exception {
        return mvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\",\"registrationCode\":\"%s\"}".formatted(email, password, code)));
    }

    @Test
    void validSignUpCreatesTheAccountAndSignsItIn() throws Exception {
        register("new.user@example.com", PASSWORD, CODE).andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.expiresIn", is(1800)))
                .andExpect(jsonPath("$.email", is("new.user@example.com")))
                .andExpect(jsonPath("$.*", hasSize(4)));

        AppUser stored = users.findByEmail("new.user@example.com").orElseThrow();
        assertThat(stored.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(stored.getPasswordHash()).startsWith("{bcrypt}").doesNotContain(PASSWORD);
        assertThat(encoder.matches(PASSWORD, stored.getPasswordHash())).isTrue();
    }

    @Test
    void theTokenFromSignUpUnlocksTheVehicleApi() throws Exception {
        String body = register("new.user@example.com", PASSWORD, CODE).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        mvc.perform(get("/api/v1/vehicles")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/vehicles").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void theNewCredentialsWorkOnTheNormalLoginEndpointToo() throws Exception {
        register("new.user@example.com", PASSWORD, CODE).andExpect(status().isCreated());

        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"NEW.user@example.com\",\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void aWrongCodeIsRejectedWith403AndCreatesNothing() throws Exception {
        register("new.user@example.com", PASSWORD, "definitely-not-the-code").andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.title", is("Registration not allowed")))
                .andExpect(jsonPath("$.detail", is("That registration code is not valid.")))
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        assertThat(users.count()).isZero();
    }

    @Test
    void aWrongCodeLooksIdenticalWhetherOrNotTheEmailIsTaken() throws Exception {
        users.save(new AppUser("taken@example.com", encoder.encode(PASSWORD), UserRole.ADMIN));

        String existing = register("taken@example.com", PASSWORD, "wrong-code-value-1").andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();
        String fresh = register("fresh@example.com", PASSWORD, "wrong-code-value-1").andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(existing, "$.detail")).isEqualTo(JsonPath.<String>read(fresh, "$.detail"));
    }

    @Test
    void anExistingEmailGets409EvenWithDifferentCase() throws Exception {
        register("new.user@example.com", PASSWORD, CODE).andExpect(status().isCreated());

        register("NEW.USER@example.com", "another-long-password-1", CODE).andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.title", is("Duplicate email")))
                .andExpect(jsonPath("$.detail", is("An account with this email already exists.")));
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void invalidFieldsReturn400WithFieldErrors() throws Exception {
        register("not-an-email", "short", "").andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("email", "password", "registrationCode")))
                .andExpect(jsonPath("$.detail", not(containsString("short"))));
        assertThat(users.count()).isZero();
    }

    @Test
    void passwordsOverTheBcryptLimitAreRejectedAs400() throws Exception {
        register("new.user@example.com", "é".repeat(40), CODE).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("password")));
        assertThat(users.count()).isZero();
    }

    @Test
    void signUpDoesNotNeedAnExistingSession() throws Exception {
        // Public by design; the registration code is the gate.
        register("new.user@example.com", PASSWORD, CODE).andExpect(status().isCreated());
    }

    @Test
    void neitherPasswordsCodesNorTokensAppearInLogs(CapturedOutput output) throws Exception {
        String wrongCode = "wrong-code-unique-marker-9";
        String body = register("new.user@example.com", PASSWORD, CODE).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");
        register("new.user@example.com", PASSWORD, CODE);                 // duplicate
        register("other@example.com", PASSWORD, wrongCode);               // wrong code
        register("bad", "short-marker", "");                              // validation
        mvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content("{broken " + PASSWORD + " " + CODE));

        assertThat(output.getAll())
                .doesNotContain(PASSWORD)
                .doesNotContain(CODE)
                .doesNotContain(wrongCode)
                .doesNotContain("short-marker")
                .doesNotContain(token);
    }

    @Test
    void getOnTheRegisterUrlCreatesNothing() throws Exception {
        mvc.perform(get(REGISTER + "?email=a@b.com&password=" + PASSWORD + "&registrationCode=" + CODE))
                .andExpect(status().isUnauthorized());
        assertThat(users.count()).isZero();
    }
}
