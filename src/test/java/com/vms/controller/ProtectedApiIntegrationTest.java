package com.vms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vms.entity.Vehicle;
import com.vms.entity.VehicleStatus;
import com.vms.repository.VehicleRepository;
import com.vms.support.TestTokens;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

/** Every vehicle operation must reject callers that are not holding a valid token. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ProtectedApiIntegrationTest {

    private static final String VEHICLES = "/api/v1/vehicles";
    private static final String BODY = """
            {"vehicleNumber":"AUTH-1","company":"Acme","type":"Truck","brand":"Volvo","model":"FH16","status":"ACTIVE"}
            """;

    @Autowired
    MockMvc mvc;

    @Autowired
    VehicleRepository vehicles;

    @Autowired
    JwtEncoder serverEncoder;

    @Value("${app.security.jwt.secret}")
    String secret;

    long existingId;

    @BeforeEach
    void seed() {
        vehicles.deleteAll();
        existingId = vehicles.save(new Vehicle("SEED-1", "Acme", "Van", "Ford", "Transit", VehicleStatus.ACTIVE)).getId();
    }

    private String validToken() {
        return TestTokens.token(serverEncoder, "vms-backend", "admin@example.com", Instant.now(), Duration.ofMinutes(5));
    }

    private RequestBuilder crud(String operation, String authorization) {
        var builder = switch (operation) {
            case "list" -> get(VEHICLES);
            case "read" -> get(VEHICLES + "/" + existingId);
            case "create" -> post(VEHICLES).contentType(MediaType.APPLICATION_JSON).content(BODY);
            case "update" -> put(VEHICLES + "/" + existingId).contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY);
            case "delete" -> delete(VEHICLES + "/" + existingId);
            default -> throw new IllegalArgumentException(operation);
        };
        return authorization == null ? builder : builder.header(HttpHeaders.AUTHORIZATION, authorization);
    }

    private static final String UPDATE_BODY = """
            {"vehicleNumber":"UPD-1","company":"Acme","type":"Truck","brand":"Volvo","model":"FH16","status":"ACTIVE"}
            """;

    private static final String[] OPERATIONS = {"list", "read", "create", "update", "delete"};

    private ResultActions perform(String operation, String authorization) throws Exception {
        return mvc.perform(crud(operation, authorization));
    }

    @Test
    void everyOperationRejectsRequestsWithoutAToken() throws Exception {
        for (String operation : OPERATIONS) {
            perform(operation, null).andExpect(status().isUnauthorized())
                    .andExpect(header().string("Content-Type", "application/problem+json"))
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.detail", is("Authentication is required to access this resource.")));
        }
    }

    @Test
    void rejectedRequestsChangeNothing() throws Exception {
        perform("create", null);
        perform("update", null);
        perform("delete", null);

        assertThat(vehicles.count()).isEqualTo(1);
        assertThat(vehicles.findById(existingId).orElseThrow().getVehicleNumber()).isEqualTo("SEED-1");
    }

    @Test
    void everyOperationWorksWithAValidToken() throws Exception {
        String bearer = "Bearer " + validToken();

        perform("list", bearer).andExpect(status().isOk());
        perform("read", bearer).andExpect(status().isOk());
        perform("create", bearer).andExpect(status().isCreated());
        perform("update", bearer).andExpect(status().isOk());
        perform("delete", bearer).andExpect(status().isNoContent());
    }

    @Test
    void garbageTokensAreRejected() throws Exception {
        for (String operation : OPERATIONS) {
            perform(operation, "Bearer definitely-not-a-jwt").andExpect(status().isUnauthorized());
        }
    }

    @Test
    void expiredTokensAreRejectedWithASessionExpiredMessage() throws Exception {
        String expired = TestTokens.token(serverEncoder, "vms-backend", "admin@example.com",
                Instant.now().minus(Duration.ofHours(2)), Duration.ofMinutes(30));

        perform("list", "Bearer " + expired).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail", is("Your session has expired. Please sign in again.")));
        assertThat(vehicles.count()).isEqualTo(1);
        perform("delete", "Bearer " + expired).andExpect(status().isUnauthorized());
        assertThat(vehicles.count()).isEqualTo(1);
    }

    @Test
    void tokensSignedWithAnotherKeyAreRejected() throws Exception {
        String forged = TestTokens.token(TestTokens.encoderFor("a-completely-different-signing-key-0123456789"),
                "vms-backend", "admin@example.com", Instant.now(), Duration.ofMinutes(5));

        perform("list", "Bearer " + forged).andExpect(status().isUnauthorized());
        perform("delete", "Bearer " + forged).andExpect(status().isUnauthorized());
        assertThat(vehicles.count()).isEqualTo(1);
    }

    @Test
    void tokensFromAnotherIssuerAreRejected() throws Exception {
        String foreign = TestTokens.token(serverEncoder, "someone-else", "admin@example.com", Instant.now(),
                Duration.ofMinutes(5));

        perform("list", "Bearer " + foreign).andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedPayloadsAreRejected() throws Exception {
        String[] parts = validToken().split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.replace("admin@example.com", "root@example.com").getBytes());

        perform("list", "Bearer " + parts[0] + "." + forgedPayload + "." + parts[2]).andExpect(status().isUnauthorized());
    }

    @Test
    void unsignedTokensAreRejected() throws Exception {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"iss\":\"vms-backend\",\"sub\":\"admin@example.com\",\"exp\":" + (Instant.now().getEpochSecond() + 600) + "}")
                        .getBytes());

        perform("list", "Bearer " + header + "." + payload + ".").andExpect(status().isUnauthorized());
    }

    @Test
    void otherAuthenticationSchemesAreNotAccepted() throws Exception {
        String basic = "Basic " + Base64.getEncoder().encodeToString("admin@example.com:anything".getBytes());

        perform("list", basic).andExpect(status().isUnauthorized());
        mvc.perform(get(VEHICLES + "?access_token=" + validToken())).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownAndNonApiPathsDoNotRevealAnythingToAnonymousCallers() throws Exception {
        mvc.perform(get("/api/v1/does-not-exist")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(get("/")).andExpect(status().isUnauthorized());
    }

    @Test
    void theHealthCheckStaysPublicAndRevealsNoDetails() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.components.db.details").doesNotExist());
    }

    @Test
    void securityHeadersAreSent() throws Exception {
        perform("list", null).andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    }
}
