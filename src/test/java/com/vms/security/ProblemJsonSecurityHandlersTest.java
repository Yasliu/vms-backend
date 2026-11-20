package com.vms.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

/** 401 and 403 are written by the security filters, so they must match the shape of the MVC errors. */
class ProblemJsonSecurityHandlersTest {

    private final ProblemJsonSecurityHandlers handlers = new ProblemJsonSecurityHandlers();

    @Test
    void unauthorizedBodyHasTheSameFieldsAsEveryOtherError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/vehicles/999999");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handlers.commence(request, response, new BadCredentialsException("x"));

        String body = response.getContentAsString();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(JsonPath.<String>read(body, "$.type")).isEqualTo("about:blank");
        assertThat(JsonPath.<String>read(body, "$.title")).isEqualTo("Unauthorized");
        assertThat(JsonPath.<Integer>read(body, "$.status")).isEqualTo(401);
        assertThat(JsonPath.<String>read(body, "$.detail")).isNotBlank();
        assertThat(JsonPath.<String>read(body, "$.instance")).isEqualTo("/api/v1/vehicles/999999");
    }

    @Test
    void forbiddenBodyHasAnInstanceToo() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/vehicles/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handlers.handle(request, response, new AccessDeniedException("x"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(JsonPath.<String>read(response.getContentAsString(), "$.instance")).isEqualTo("/api/v1/vehicles/1");
    }

    @Test
    void aHostileUrlCannotBreakOutOfTheJson() throws Exception {
        String hostile = "/api/v1/vehicles/\"},\"injected\":\"yes\\";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", hostile);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handlers.commence(request, response, new BadCredentialsException("x"));

        String body = response.getContentAsString();
        assertThat(JsonPath.<String>read(body, "$.instance")).isEqualTo(hostile);
        assertThat(JsonPath.<Object>read(body, "$")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .doesNotContainKey("injected");
    }
}
