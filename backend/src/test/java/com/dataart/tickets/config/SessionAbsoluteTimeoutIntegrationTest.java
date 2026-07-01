package com.dataart.tickets.config;

import com.dataart.tickets.auth.User;
import com.dataart.tickets.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTS-046 integration: with the app's {@code Clock} overridden by a mutable test clock, log in
 * (stamping the session), fast-forward past the absolute cap, and confirm the next request is
 * rejected with 401 UNAUTHENTICATED even though the session is still within its idle window — while
 * a request within the cap keeps working.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
// Let the mutable test clock override the app's systemUTC `clock` bean.
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class SessionAbsoluteTimeoutIntegrationTest {

    private static final String EMAIL = "session@example.com";
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    /** Replace the systemUTC clock with one the test can fast-forward. */
    @TestConfiguration
    static class MutableClockConfig {
        @Bean
        @Primary
        Clock clock() {
            return new MutableClock(T0);
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private Clock clock;

    @BeforeEach
    void seed() {
        users.deleteAll();
        User verified = new User(EMAIL, passwordEncoder.encode("password1"));
        verified.setEmailVerified(true);
        users.save(verified);
        ((MutableClock) clock).set(T0);
    }

    // AC-2: a request comfortably within the 8h cap works normally.
    @Test
    void withinCapSessionStillWorks() throws Exception {
        MockHttpSession session = login();
        ((MutableClock) clock).advance(Duration.ofHours(7));

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    // AC-1 + AC-4: past the cap the session is rejected with the standard 401 UNAUTHENTICATED.
    @Test
    void pastCapIsRejectedWith401() throws Exception {
        MockHttpSession session = login();
        ((MutableClock) clock).advance(Duration.ofHours(9)); // cap is 8h

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private MockHttpSession login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password1\"}".formatted(EMAIL)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /** Minimal fast-forwardable clock for deterministic session-expiry tests. */
    static final class MutableClock extends Clock {
        private volatile Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration by) {
            this.instant = this.instant.plus(by);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
