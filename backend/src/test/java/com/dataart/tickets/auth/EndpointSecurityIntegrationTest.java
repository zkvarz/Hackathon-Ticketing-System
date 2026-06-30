package com.dataart.tickets.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint security + CSRF (HTS-013). Uses a not-yet-implemented business path (`/api/teams`,
 * controller arrives in HTS-015) to exercise the filter chain: security runs before routing,
 * so these assertions are about authn/authz/CSRF, not the (absent) handler.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EndpointSecurityIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    // AC-1: an unauthenticated business request → 401 in the standard error model.
    @Test
    void unauthenticatedBusinessEndpointReturns401() throws Exception {
        mockMvc.perform(get("/api/teams"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // AC-2: public allowlist endpoints are reachable anonymously.
    @Test
    void publicEndpointsReachableAnonymously() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // AC-3: an authenticated state-changing request without a CSRF token → 403.
    @Test
    void postWithoutCsrfReturns403() throws Exception {
        mockMvc.perform(post("/api/teams").with(user("u@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // AC-4: with auth + a valid CSRF token, the request passes the security filters (it then
    // 404s only because the HTS-015 controller isn't here yet — i.e. it is NOT 401/403).
    @Test
    void postWithAuthAndCsrfPassesSecurity() throws Exception {
        mockMvc.perform(post("/api/teams").with(user("u@example.com")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // Boundary: a near-miss business path is not anonymously reachable.
    @Test
    void nearMissPathRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/epics"))
                .andExpect(status().isUnauthorized());
    }
}
