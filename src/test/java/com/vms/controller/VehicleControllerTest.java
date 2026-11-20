package com.vms.controller;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vms.config.SecurityConfig;
import com.vms.dto.VehicleCreateRequest;
import com.vms.dto.VehicleResponse;
import com.vms.dto.VehicleUpdateRequest;
import com.vms.entity.VehicleStatus;
import com.vms.exception.DuplicateVehicleNumberException;
import com.vms.exception.ResourceNotFoundException;
import com.vms.service.VehicleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web-layer slice: controller, error handler and real security rules, with the service mocked. */
@WebMvcTest(VehicleController.class)
@Import(SecurityConfig.class)
@WithMockUser
class VehicleControllerTest {

    private static final String VALID_BODY = """
            {"vehicleNumber":"DHAKA-1","company":"Acme","type":"Truck","brand":"Volvo","model":"FH16"}
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    VehicleService service;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private static VehicleResponse response(long id) {
        return new VehicleResponse(id, "DHAKA-1", "Acme", "Truck", "Volvo", "FH16", VehicleStatus.ACTIVE);
    }

    @Test
    void createDelegatesToServiceAndReturns201WithLocation() throws Exception {
        when(service.create(any(VehicleCreateRequest.class))).thenReturn(response(7));

        mvc.perform(post("/api/v1/vehicles").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/vehicles/7")))
                .andExpect(jsonPath("$.id", is(7)));

        verify(service).create(new VehicleCreateRequest("DHAKA-1", "Acme", "Truck", "Volvo", "FH16", null));
    }

    @Test
    void invalidRequestIsRejectedBeforeReachingTheService() throws Exception {
        mvc.perform(post("/api/v1/vehicles").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void duplicateVehicleNumberFromServiceMapsTo409() throws Exception {
        when(service.create(any())).thenThrow(new DuplicateVehicleNumberException("DHAKA-1"));

        mvc.perform(post("/api/v1/vehicles").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Duplicate vehicle number")))
                .andExpect(jsonPath("$.vehicleNumber", is("DHAKA-1")));
    }

    @Test
    void getMapsNotFoundTo404() throws Exception {
        when(service.get(5L)).thenThrow(new ResourceNotFoundException("Vehicle", 5L));

        mvc.perform(get("/api/v1/vehicles/5")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", is("Vehicle with id 5 was not found")));
    }

    @Test
    void updatePassesIdAndBodyToService() throws Exception {
        when(service.update(eq(3L), any(VehicleUpdateRequest.class))).thenReturn(response(3));

        mvc.perform(put("/api/v1/vehicles/3").contentType(MediaType.APPLICATION_JSON).content("""
                {"vehicleNumber":"DHAKA-1","company":"Acme","type":"Truck","brand":"Volvo","model":"FH16","status":"ACTIVE"}
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.id", is(3)));

        verify(service).update(3L,
                new VehicleUpdateRequest("DHAKA-1", "Acme", "Truck", "Volvo", "FH16", VehicleStatus.ACTIVE));
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete("/api/v1/vehicles/4")).andExpect(status().isNoContent());

        verify(service).delete(4L);
    }

    @Test
    void unexpectedErrorsReturnGeneric500WithoutLeakingInternals() throws Exception {
        when(service.get(1L)).thenThrow(new IllegalStateException("jdbc:postgresql://secret-host/db exploded"));

        mvc.perform(get("/api/v1/vehicles/1")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title", is("Internal server error")))
                .andExpect(jsonPath("$.detail", not(containsString("secret-host"))));
    }
}
