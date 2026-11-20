package com.vms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vms.entity.Vehicle;
import com.vms.entity.VehicleStatus;
import com.vms.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Full-stack CRUD tests as an authenticated user: HTTP -> controller -> service -> JPA -> H2. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@WithMockUser
class VehicleApiIntegrationTest {

    private static final String BASE = "/api/v1/vehicles";
    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    MockMvc mvc;

    @Autowired
    VehicleRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    private static String json(String number, String status) {
        String statusField = status == null ? "" : ",\"status\":\"" + status + "\"";
        return """
                {"vehicleNumber":"%s","company":"Acme","type":"Truck","brand":"Volvo","model":"FH16"%s}
                """.formatted(number, statusField);
    }

    private ResultActions createRaw(String body) throws Exception {
        return mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions createVehicle(String number) throws Exception {
        return createRaw(json(number, null));
    }

    private long createAndGetId(String number) throws Exception {
        String body = createVehicle(number).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions updateVehicle(long id, String number, String status) throws Exception {
        return mvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(json(number, status)));
    }

    @Nested
    class Create {

        @Test
        void returns201WithLocationAndOnlyDtoFields() throws Exception {
            createVehicle("  dhaka-11-2345 ").andExpect(status().isCreated())
                    .andExpect(header().string("Location", endsWith(BASE + "/" + firstId())))
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.vehicleNumber", is("DHAKA-11-2345")))
                    .andExpect(jsonPath("$.company", is("Acme")))
                    .andExpect(jsonPath("$.type", is("Truck")))
                    .andExpect(jsonPath("$.brand", is("Volvo")))
                    .andExpect(jsonPath("$.model", is("FH16")))
                    .andExpect(jsonPath("$.status", is("ACTIVE")))
                    .andExpect(jsonPath("$.*", hasSize(7)));
        }

        private long firstId() {
            return repository.findAll().isEmpty() ? -1 : repository.findAll().get(0).getId();
        }

        @Test
        void honoursExplicitStatusAndPersistsTheVehicle() throws Exception {
            createRaw(json("DHAKA-1", "UNDER_MAINTENANCE")).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status", is("UNDER_MAINTENANCE")));

            assertThat(repository.findAll()).singleElement().satisfies(v -> {
                assertThat(v.getVehicleNumber()).isEqualTo("DHAKA-1");
                assertThat(v.getStatus()).isEqualTo(VehicleStatus.UNDER_MAINTENANCE);
            });
        }

        @Test
        void missingAndBlankFieldsReturn400WithFieldErrors() throws Exception {
            createRaw("{\"vehicleNumber\":\"   \",\"company\":\"\",\"type\":\"T\",\"brand\":\"B\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Content-Type", PROBLEM_JSON))
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.title", is("Validation failed")))
                    .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("vehicleNumber", "company", "model")));
            assertThat(repository.count()).isZero();
        }

        @Test
        void tooLongValuesReturn400() throws Exception {
            createRaw(json("X".repeat(31), null)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field", is("vehicleNumber")));
        }

        @Test
        void unknownStatusReturn400() throws Exception {
            createRaw(json("DHAKA-1", "FLYING")).andExpect(status().isBadRequest())
                    .andExpect(header().string("Content-Type", PROBLEM_JSON));
        }

        @Test
        void malformedJsonReturns400() throws Exception {
            createRaw("{not json").andExpect(status().isBadRequest())
                    .andExpect(header().string("Content-Type", PROBLEM_JSON));
        }

        @Test
        void missingBodyReturns400() throws Exception {
            mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
        }

        @Test
        void wrongContentTypeReturns415() throws Exception {
            mvc.perform(post(BASE).contentType(MediaType.TEXT_PLAIN).content("x"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(header().string("Content-Type", PROBLEM_JSON));
        }

        @Test
        void duplicateNumberReturns409EvenWithDifferentCaseOrSpacing() throws Exception {
            createVehicle("DHAKA 1").andExpect(status().isCreated());

            createVehicle("  dhaka    1 ").andExpect(status().isConflict())
                    .andExpect(header().string("Content-Type", PROBLEM_JSON))
                    .andExpect(jsonPath("$.status", is(409)))
                    .andExpect(jsonPath("$.title", is("Duplicate vehicle number")))
                    .andExpect(jsonPath("$.vehicleNumber", is("DHAKA 1")));
            assertThat(repository.count()).isEqualTo(1);
        }
    }

    @Nested
    class Read {

        @Test
        void getReturnsTheVehicle() throws Exception {
            long id = createAndGetId("DHAKA-1");

            mvc.perform(get(BASE + "/" + id)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is((int) id)))
                    .andExpect(jsonPath("$.vehicleNumber", is("DHAKA-1")));
        }

        @Test
        void unknownIdReturns404() throws Exception {
            mvc.perform(get(BASE + "/999999")).andExpect(status().isNotFound())
                    .andExpect(header().string("Content-Type", PROBLEM_JSON))
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(jsonPath("$.title", is("Resource not found")));
        }

        @Test
        void nonNumericIdReturns400() throws Exception {
            mvc.perform(get(BASE + "/abc")).andExpect(status().isBadRequest())
                    .andExpect(header().string("Content-Type", PROBLEM_JSON));
        }

        @Test
        void emptyListReturns200WithEmptyPage() throws Exception {
            mvc.perform(get(BASE)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements", is(0)));
        }

        @Test
        void listIsPagedAndSortedByIdByDefault() throws Exception {
            createVehicle("DHAKA-1").andExpect(status().isCreated());
            createVehicle("DHAKA-2").andExpect(status().isCreated());
            createVehicle("DHAKA-3").andExpect(status().isCreated());

            mvc.perform(get(BASE).param("size", "2").param("page", "0")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[*].vehicleNumber", containsInAnyOrder("DHAKA-1", "DHAKA-2")))
                    .andExpect(jsonPath("$.content[0].vehicleNumber", is("DHAKA-1")))
                    .andExpect(jsonPath("$.page", is(0)))
                    .andExpect(jsonPath("$.size", is(2)))
                    .andExpect(jsonPath("$.totalElements", is(3)))
                    .andExpect(jsonPath("$.totalPages", is(2)));

            mvc.perform(get(BASE).param("size", "2").param("page", "1"))
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].vehicleNumber", is("DHAKA-3")));
        }

        @Test
        void listCanBeSortedByAnyVehicleField() throws Exception {
            createVehicle("B-1").andExpect(status().isCreated());
            createVehicle("A-1").andExpect(status().isCreated());

            mvc.perform(get(BASE).param("sort", "vehicleNumber,asc"))
                    .andExpect(jsonPath("$.content[0].vehicleNumber", is("A-1")));
        }

        @Test
        void listFiltersByStatusAndSearchText() throws Exception {
            createRaw(json("DHAKA-1", "ACTIVE")).andExpect(status().isCreated());
            createRaw(json("DHAKA-2", "INACTIVE")).andExpect(status().isCreated());
            createRaw("""
                    {"vehicleNumber":"CTG-9","company":"Zeta","type":"Van","brand":"Toyota","model":"HiAce","status":"INACTIVE"}
                    """).andExpect(status().isCreated());

            mvc.perform(get(BASE).param("status", "INACTIVE")).andExpect(jsonPath("$.totalElements", is(2)));
            mvc.perform(get(BASE).param("q", "toyota")).andExpect(jsonPath("$.totalElements", is(1)))
                    .andExpect(jsonPath("$.content[0].vehicleNumber", is("CTG-9")));
            mvc.perform(get(BASE).param("q", "dhaka").param("status", "INACTIVE"))
                    .andExpect(jsonPath("$.totalElements", is(1)))
                    .andExpect(jsonPath("$.content[0].vehicleNumber", is("DHAKA-2")));
        }

        @Test
        void invalidStatusFilterReturns400() throws Exception {
            mvc.perform(get(BASE).param("status", "FLYING")).andExpect(status().isBadRequest())
                    .andExpect(header().string("Content-Type", PROBLEM_JSON));
        }

        @Test
        void unknownSortPropertyReturns400NotServerError() throws Exception {
            mvc.perform(get(BASE).param("sort", "doesNotExist,asc")).andExpect(status().isBadRequest())
                    .andExpect(header().string("Content-Type", PROBLEM_JSON));
        }

        @Test
        void pageSizeIsCapped() throws Exception {
            mvc.perform(get(BASE).param("size", "100000")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.size", lessThanOrEqualTo(100)));
        }
    }

    @Nested
    class Update {

        @Test
        void returns200AndPersistsChanges() throws Exception {
            long id = createAndGetId("DHAKA-1");

            mvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content("""
                    {"vehicleNumber":"ctg-5","company":"Zeta","type":"Van","brand":"Toyota","model":"HiAce","status":"INACTIVE"}
                    """)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is((int) id)))
                    .andExpect(jsonPath("$.vehicleNumber", is("CTG-5")))
                    .andExpect(jsonPath("$.company", is("Zeta")))
                    .andExpect(jsonPath("$.status", is("INACTIVE")));

            Vehicle stored = repository.findById(id).orElseThrow();
            assertThat(stored.getVehicleNumber()).isEqualTo("CTG-5");
            assertThat(stored.getBrand()).isEqualTo("Toyota");
        }

        @Test
        void vehicleMayKeepItsOwnNumber() throws Exception {
            long id = createAndGetId("DHAKA-1");

            updateVehicle(id, "dhaka-1", "INACTIVE").andExpect(status().isOk())
                    .andExpect(jsonPath("$.vehicleNumber", is("DHAKA-1")));
        }

        @Test
        void takingAnotherVehiclesNumberReturns409AndChangesNothing() throws Exception {
            createAndGetId("DHAKA-1");
            long second = createAndGetId("DHAKA-2");

            updateVehicle(second, " dhaka-1", "ACTIVE").andExpect(status().isConflict())
                    .andExpect(header().string("Content-Type", PROBLEM_JSON))
                    .andExpect(jsonPath("$.title", is("Duplicate vehicle number")));

            assertThat(repository.findById(second).orElseThrow().getVehicleNumber()).isEqualTo("DHAKA-2");
        }

        @Test
        void changingNumberFreesTheOldOne() throws Exception {
            long id = createAndGetId("DHAKA-1");
            updateVehicle(id, "DHAKA-9", "ACTIVE").andExpect(status().isOk());

            createVehicle("DHAKA-1").andExpect(status().isCreated());
        }

        @Test
        void unknownIdReturns404() throws Exception {
            updateVehicle(999999, "DHAKA-1", "ACTIVE").andExpect(status().isNotFound());
        }

        @Test
        void missingStatusReturns400BecauseUpdateIsAFullReplacement() throws Exception {
            long id = createAndGetId("DHAKA-1");

            updateVehicle(id, "DHAKA-1", null).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field", is("status")));
        }

        @Test
        void invalidBodyReturns400AndChangesNothing() throws Exception {
            long id = createAndGetId("DHAKA-1");

            mvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"vehicleNumber\":\"\",\"status\":\"ACTIVE\"}")).andExpect(status().isBadRequest());

            assertThat(repository.findById(id).orElseThrow().getVehicleNumber()).isEqualTo("DHAKA-1");
        }
    }

    @Nested
    class Delete {

        @Test
        void returns204ThenVehicleIsGone() throws Exception {
            long id = createAndGetId("DHAKA-1");

            mvc.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));
            mvc.perform(get(BASE + "/" + id)).andExpect(status().isNotFound());
            assertThat(repository.count()).isZero();
        }

        @Test
        void unknownIdReturns404() throws Exception {
            mvc.perform(delete(BASE + "/999999")).andExpect(status().isNotFound());
        }

        @Test
        void deletingFreesTheNumberForReuse() throws Exception {
            long id = createAndGetId("DHAKA-1");
            mvc.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent());

            createVehicle("DHAKA-1").andExpect(status().isCreated());
        }
    }

    @Test
    void unsupportedMethodReturns405AsProblemJson() throws Exception {
        long id = createAndGetId("DHAKA-1");

        mvc.perform(patch(BASE + "/" + id)).andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Content-Type", PROBLEM_JSON));
    }
}
