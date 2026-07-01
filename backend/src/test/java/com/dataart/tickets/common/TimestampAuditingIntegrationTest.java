package com.dataart.tickets.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTS-047 — verifies the single-source, Clock-driven timestamps end-to-end with a fast-forwardable
 * test clock: on create both timestamps equal the clock instant; the persisted value equals the one
 * the API returned; a real change advances {@code modified_at} to the new clock instant while
 * {@code created_at} stays; and a no-op update does not advance it (AMB-3). Uses Team as the subject
 * (same {@code BaseEntity} auditing applies to Epic/Ticket).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class TimestampAuditingIntegrationTest {

    private static final String PRINCIPAL = "u@example.com";
    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

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
    private ObjectMapper mapper;
    @Autowired
    private Clock clock;

    // AC-1/AC-4: create stamps both timestamps from the clock; persisted == returned.
    @Test
    void createStampsBothFromClockAndPersists() throws Exception {
        ((MutableClock) clock).set(T0);

        String created = createTeam("Auditing " + UUID.randomUUID());
        assertThat(instant(created, "createdAt")).isEqualTo(T0);
        assertThat(instant(created, "modifiedAt")).isEqualTo(T0);

        // Persisted == returned: a fresh GET reports the same instants.
        String id = mapper.readTree(created).get("id").asText();
        String fetched = mockMvc.perform(get("/api/teams/" + id).with(user(PRINCIPAL)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(instant(fetched, "modifiedAt")).isEqualTo(T0);
    }

    // AC-4 + AMB-3: a real change advances modified_at to the new clock instant; created_at stays.
    @Test
    void realChangeAdvancesModifiedAtToClock() throws Exception {
        ((MutableClock) clock).set(T0);
        String created = createTeam("Rename me " + UUID.randomUUID());
        String id = mapper.readTree(created).get("id").asText();

        Instant t1 = T0.plus(Duration.ofHours(3));
        ((MutableClock) clock).set(t1);
        String renamed = rename(id, "Renamed " + UUID.randomUUID());

        assertThat(instant(renamed, "modifiedAt")).isEqualTo(t1);
        assertThat(instant(renamed, "createdAt")).isEqualTo(T0); // updatable=false
    }

    // AC-2 (AMB-3): saving the same name is a no-op — modified_at does not advance.
    @Test
    void noOpUpdateDoesNotAdvanceModifiedAt() throws Exception {
        ((MutableClock) clock).set(T0);
        String name = "Stable " + UUID.randomUUID();
        String created = createTeam(name);
        String id = mapper.readTree(created).get("id").asText();

        ((MutableClock) clock).set(T0.plus(Duration.ofHours(5)));
        String sameName = rename(id, name); // identical value → not dirty

        assertThat(instant(sameName, "modifiedAt")).isEqualTo(T0);
    }

    private String createTeam(String name) throws Exception {
        return mockMvc.perform(post("/api/teams").with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String rename(String id, String name) throws Exception {
        return mockMvc.perform(put("/api/teams/" + id).with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private Instant instant(String json, String field) throws Exception {
        return Instant.parse(mapper.readTree(json).get(field).asText());
    }

    /** Fast-forwardable clock so timestamp advancement is deterministic. */
    static final class MutableClock extends Clock {
        private volatile Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
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
