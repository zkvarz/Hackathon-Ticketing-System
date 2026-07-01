package com.dataart.tickets.ticket;

import com.dataart.tickets.auth.User;
import com.dataart.tickets.auth.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * State-change endpoint over the API against real Postgres (HTS-027, FR-K7/FR-B6): a PATCH
 * persists the new state and advances modified_at, any-to-any transitions are accepted, an unknown
 * state is rejected, and a missing ticket → 404.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class StateChangeIntegrationTest {

    private static final String PRINCIPAL = "u@example.com";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private UserRepository users;

    @BeforeEach
    void seedUser() {
        users.findByEmail(PRINCIPAL).orElseGet(() -> users.save(new User(PRINCIPAL, "hash")));
    }

    private String createTeam(String name) throws Exception {
        String body = mockMvc.perform(post("/api/teams").with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }

    private JsonNode createTicket(String teamId) throws Exception {
        String body = mockMvc.perform(post("/api/tickets").with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":\"%s\",\"type\":\"bug\",\"state\":\"new\",\"title\":\"T\",\"body\":\"B\"}"
                                .formatted(teamId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    // AC-1 + AC-3: any-to-any change persists and advances modified_at.
    @Test
    void patchStatePersistsAndAdvancesModifiedAt() throws Exception {
        String teamId = createTeam("Board");
        String id = createTicket(teamId).get("id").asText();

        String fetched = mockMvc.perform(get("/api/tickets/" + id).with(user(PRINCIPAL)))
                .andReturn().getResponse().getContentAsString();
        Instant before = Instant.parse(mapper.readTree(fetched).get("modifiedAt").asText());

        // new -> done directly (skipping intermediate states) is allowed (FR-B6).
        String patched = mockMvc.perform(patch("/api/tickets/" + id + "/state").with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"state\":\"done\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("done"))
                .andReturn().getResponse().getContentAsString();
        assertThat(Instant.parse(mapper.readTree(patched).get("modifiedAt").asText())).isAfter(before);

        // Re-read confirms persistence.
        mockMvc.perform(get("/api/tickets/" + id).with(user(PRINCIPAL)))
                .andExpect(jsonPath("$.state").value("done"));
    }

    // AC-2: an unknown state → 400.
    @Test
    void invalidStateReturns400() throws Exception {
        String teamId = createTeam("Invalid");
        String id = createTicket(teamId).get("id").asText();

        mockMvc.perform(patch("/api/tickets/" + id + "/state").with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"state\":\"archived\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // AC-4: state change on a non-existent ticket → 404.
    @Test
    void patchMissingTicketReturns404() throws Exception {
        mockMvc.perform(patch("/api/tickets/" + UUID.randomUUID() + "/state").with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"state\":\"in_progress\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
