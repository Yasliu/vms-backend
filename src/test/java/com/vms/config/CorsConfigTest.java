package com.vms.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:*,https://vms.example.com")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CorsConfigTest {

    @Autowired
    MockMvc mvc;

    private org.springframework.test.web.servlet.ResultActions preflight(String origin, String method)
            throws Exception {
        return mvc.perform(options("/api/v1/vehicles/1")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"));
    }

    @Test
    void allowsPreflightFromAnyLocalPortForEveryCrudMethod() throws Exception {
        for (String method : new String[] {"GET", "POST", "PUT", "DELETE"}) {
            preflight("http://localhost:54321", method).andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:54321"));
        }
    }

    @Test
    void allowsTheConfiguredProductionOrigin() throws Exception {
        preflight("https://vms.example.com", "PUT").andExpect(status().isOk());
    }

    @Test
    void rejectsOriginsThatAreNotConfigured() throws Exception {
        preflight("https://evil.example", "DELETE").andExpect(status().isForbidden());
    }

    @Test
    void rejectsMethodsTheApiDoesNotOffer() throws Exception {
        preflight("http://localhost:54321", "PATCH").andExpect(status().isForbidden());
    }

    @Test
    void preflightAllowsTheAuthorizationHeaderWithoutCredentials() throws Exception {
        mvc.perform(options("/api/v1/vehicles")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk());
    }

    @Test
    void unauthorizedResponsesStillCarryCorsHeadersSoTheBrowserCanReadThem() throws Exception {
        mvc.perform(get("/api/v1/vehicles").header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    @Test
    @WithMockUser
    void actualRequestsFromAllowedOriginsCarryTheHeader() throws Exception {
        mvc.perform(get("/api/v1/vehicles").header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }
}
