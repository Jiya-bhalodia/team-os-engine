package com.placement.teama.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import static org.junit.jupiter.api.Assertions.*;

class ServiceAuthenticationFilterTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void healthAndReadyRemainPublicWhenServiceTokenIsMissing() throws Exception {
        ServiceAuthenticationFilter filter = new ServiceAuthenticationFilter("", mapper);
        for (String path : new String[]{"/health", "/ready"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertNotNull(chain.getRequest());
            assertEquals(200, response.getStatus());
        }

        ServiceAuthenticationFilter configuredFilter = new ServiceAuthenticationFilter("test-token", mapper);
        MockHttpServletRequest healthRequest = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse healthResponse = new MockHttpServletResponse();
        MockFilterChain healthChain = new MockFilterChain();
        configuredFilter.doFilter(healthRequest, healthResponse, healthChain);
        assertNotNull(healthChain.getRequest());
        assertEquals(200, healthResponse.getStatus());
    }

    @Test
    void internalEndpointsFailClosedWhenServiceTokenIsMissing() throws Exception {
        ServiceAuthenticationFilter filter = new ServiceAuthenticationFilter("", mapper);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/slots");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(503, response.getStatus());
        assertNull(chain.getRequest());
        JsonNode body = mapper.readTree(response.getContentAsString());
        assertEquals("SERVICE_AUTH_NOT_CONFIGURED", body.path("error").path("code").asText());
        assertFalse(response.getContentAsString().contains("token"));
    }

    @Test
    void nonInternalApiRemainsAvailableWhenOptionalTokenIsMissing() throws Exception {
        ServiceAuthenticationFilter filter = new ServiceAuthenticationFilter("", mapper);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/eligibility/requests");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    void configuredServiceTokenRequiresExactBearerHeader() throws Exception {
        ServiceAuthenticationFilter filter = new ServiceAuthenticationFilter("non-secret-test-value", mapper);
        MockHttpServletRequest rejectedRequest = new MockHttpServletRequest("POST", "/internal/v1/slots");
        MockHttpServletResponse rejectedResponse = new MockHttpServletResponse();
        MockFilterChain rejectedChain = new MockFilterChain();

        filter.doFilter(rejectedRequest, rejectedResponse, rejectedChain);

        assertEquals(401, rejectedResponse.getStatus());
        assertNull(rejectedChain.getRequest());

        MockHttpServletRequest acceptedRequest = new MockHttpServletRequest("POST", "/internal/v1/slots");
        acceptedRequest.addHeader("Authorization", "Bearer non-secret-test-value");
        MockHttpServletResponse acceptedResponse = new MockHttpServletResponse();
        MockFilterChain acceptedChain = new MockFilterChain();

        filter.doFilter(acceptedRequest, acceptedResponse, acceptedChain);

        assertNotNull(acceptedChain.getRequest());
        assertEquals(200, acceptedResponse.getStatus());
    }
}
