package com.dataart.tickets.config;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.web.servlet.server.Session;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security response headers + cookie flags (HTS-033, NFR-1). Asserts the hardening headers are
 * present on responses and that the session cookie is HttpOnly with SameSite=Lax in the default
 * (local) profile. Secure is added in the prod profile (application-prod.yml).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SecurityHeadersIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ServerProperties serverProperties;

    // AC-2: hardening headers present on responses.
    @Test
    void responsesCarrySecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }

    // AC-3: the session cookie the container will emit is HttpOnly + SameSite=Lax (the flags are
    // applied by the servlet container from these resolved properties, not by MockMvc, so we
    // assert the configuration directly for a deterministic check). Secure is added in prod.
    @Test
    void sessionCookieConfiguredHttpOnlyAndSameSite() throws Exception {
        Session.Cookie cookie = serverProperties.getServlet().getSession().getCookie();
        Assertions.assertThat(cookie.getHttpOnly()).isTrue();
        Assertions.assertThat(cookie.getSameSite()).isEqualTo(Session.Cookie.SameSite.LAX);
    }
}
