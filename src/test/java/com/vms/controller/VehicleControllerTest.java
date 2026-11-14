package com.vms.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class VehicleControllerTest {

    @Autowired
    MockMvc mvc;

    private static String json(String number, String status) {
        String statusField = status == null ? "" : ",\"status\":\"" + status + "\"";
        return """
                {"vehicleNumber":"%s","company":"Acme","type":"Truck","brand":"Volvo","model":"FH16"%s}
                """.formatted(number, statusField);
    }

    private ResultActions createVehicle(String number) throws Exception {
        return mvc.perform(post("/api/v1/vehicles").contentType(MediaType.APPLICATION_JSON).content(json(number, null)));
    }

    private ResultActions updateVehicle(long id, String number, String status) throws Exception {
        return mvc.perform(put("/api/v1/vehicles/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(json(number, status)));
    }

    @Test
    void createReturns201WithNormalizedNumberAndDefaultStatus() throws Exception {
        createVehicle("  dhaka-11-2345 ").andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.vehicleNumber", is("DHAKA-11-2345")))
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    void createWithInvalidBodyReturns400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/vehicles").contentType(MediaType.APPLICATION_JSON)
                .content("{\"vehicleNumber\":\"\",\"company\":\"\",\"type\":\"T\",\"brand\":\"B\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("vehicleNumber", "company", "model")));
    }

    @Test
    void duplicateVehicleNumberReturns409EvenWithDifferentCaseOrSpacing() throws Exception {
        createVehicle("DHAKA-1").andExpect(status().isCreated());

        createVehicle(" dhaka-1 ").andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Duplicate vehicle number")))
                .andExpect(jsonPath("$.vehicleNumber", is("DHAKA-1")));
    }

    @Test
    void updateToAnotherVehiclesNumberReturns409ButKeepingOwnNumberWorks() throws Exception {
        createVehicle("DHAKA-1").andExpect(status().isCreated());
        createVehicle("DHAKA-2").andExpect(status().isCreated());

        updateVehicle(2, "DHAKA-1", "ACTIVE").andExpect(status().isConflict());
        updateVehicle(2, "dhaka-2", "INACTIVE").andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("INACTIVE")));
    }

    @Test
    void getAndUpdateUnknownVehicleReturn404() throws Exception {
        mvc.perform(get("/api/v1/vehicles/999")).andExpect(status().isNotFound());
        updateVehicle(999, "X-1", "ACTIVE").andExpect(status().isNotFound());
    }

    @Test
    void invalidStatusValueReturns400() throws Exception {
        createVehicle("DHAKA-1").andExpect(status().isCreated());
        updateVehicle(1, "DHAKA-1", "FLYING").andExpect(status().isBadRequest());
    }

    @Test
    void listSupportsStatusFilterSearchAndPaging() throws Exception {
        createVehicle("DHAKA-1").andExpect(status().isCreated());
        createVehicle("DHAKA-2").andExpect(status().isCreated());
        updateVehicle(2, "DHAKA-2", "UNDER_MAINTENANCE").andExpect(status().isOk());

        mvc.perform(get("/api/v1/vehicles").param("status", "UNDER_MAINTENANCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].vehicleNumber", is("DHAKA-2")));

        mvc.perform(get("/api/v1/vehicles").param("q", "dhaka").param("size", "1"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.totalPages", is(2)));
    }

    @Test
    void deleteRemovesVehicleAndFreesItsNumber() throws Exception {
        createVehicle("DHAKA-1").andExpect(status().isCreated());

        mvc.perform(delete("/api/v1/vehicles/1")).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/vehicles/1")).andExpect(status().isNotFound());
        createVehicle("DHAKA-1").andExpect(status().isCreated());
    }
}
