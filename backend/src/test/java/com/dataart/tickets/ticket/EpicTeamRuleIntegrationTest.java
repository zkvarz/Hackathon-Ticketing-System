package com.dataart.tickets.ticket;

import com.dataart.tickets.auth.User;
import com.dataart.tickets.auth.UserRepository;
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

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Epic-same-team enforcement over the API against real Postgres (HTS-021, FR-E7/FR-K5): a ticket
 * cannot reference an epic from another team, and moving a ticket to the epic's team makes the
 * same reference valid.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EpicTeamRuleIntegrationTest {

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

    private String createEpic(String teamId, String title) throws Exception {
        String body = mockMvc.perform(post("/api/epics").param("teamId", teamId)
                        .with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"%s\"}".formatted(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }

    private String ticketBody(String teamId, String epicId, String state) {
        String epicField = epicId == null ? "null" : "\"" + epicId + "\"";
        return """
                {"teamId":"%s","epicId":%s,"type":"bug","state":"%s","title":"T","body":"B"}
                """.formatted(teamId, epicField, state);
    }

    // AC-2 + AC-1: an epic from another team is rejected; the same epic under its own team works.
    @Test
    void crossTeamEpicRejectedSameTeamEpicAccepted() throws Exception {
        String teamA = createTeam("Alpha");
        String teamB = createTeam("Beta");
        String epicA = createEpic(teamA, "Alpha epic");

        // Team B ticket referencing team A's epic → 400 EPIC_TEAM_MISMATCH.
        mockMvc.perform(post("/api/tickets").with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ticketBody(teamB, epicA, "new")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EPIC_TEAM_MISMATCH"));

        // Team A ticket referencing team A's epic → 201.
        mockMvc.perform(post("/api/tickets").with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ticketBody(teamA, epicA, "new")))
                .andExpect(status().isCreated());
    }

    // AC-3: a team move that keeps a now-cross-team epic is rejected; clearing the epic succeeds.
    @Test
    void teamMoveWithCrossTeamEpicRejectedThenClearedSucceeds() throws Exception {
        String teamA = createTeam("Gamma");
        String teamB = createTeam("Delta");
        String epicA = createEpic(teamA, "Gamma epic");

        String ticketId = mapper.readTree(mockMvc.perform(post("/api/tickets").with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ticketBody(teamA, epicA, "new")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        // Move to team B but keep team A's epic → rejected.
        mockMvc.perform(put("/api/tickets/" + ticketId).with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ticketBody(teamB, epicA, "new")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EPIC_TEAM_MISMATCH"));

        // Move to team B and clear the epic → succeeds.
        mockMvc.perform(put("/api/tickets/" + ticketId).with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ticketBody(teamB, null, "new")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(teamB))
                .andExpect(jsonPath("$.epicId").value(nullValue()));
    }
}
