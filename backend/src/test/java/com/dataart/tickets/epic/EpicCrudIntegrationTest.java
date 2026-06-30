package com.dataart.tickets.epic;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Epic CRUD over the API against real Postgres (HTS-017): scoping by team, validation,
 * team-immutability, referenced-delete, and the cross-module team delete-block once an epic
 * exists (validates the HTS-015 reference-counter mechanism end to end).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EpicCrudIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;

    private String createTeam(String name) throws Exception {
        String body = mockMvc.perform(post("/api/teams").with(user("u@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }

    private String createEpic(String teamId, String title) throws Exception {
        String body = mockMvc.perform(post("/api/epics").param("teamId", teamId)
                        .with(user("u@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"%s\"}".formatted(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }

    // AC-1: create under a team; list by team returns it.
    @Test
    void createThenListByTeam() throws Exception {
        String teamId = createTeam("Payments");
        createEpic(teamId, "Checkout reliability");

        mockMvc.perform(get("/api/epics").param("teamId", teamId).with(user("u@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Checkout reliability"))
                .andExpect(jsonPath("$[0].teamId").value(teamId));
    }

    // AC-2: blank title and over-length title → 400.
    @Test
    void titleValidation() throws Exception {
        String teamId = createTeam("Billing");

        mockMvc.perform(post("/api/epics").param("teamId", teamId).with(user("u@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        String tooLong = "a".repeat(201);
        mockMvc.perform(post("/api/epics").param("teamId", teamId).with(user("u@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"%s\"}".formatted(tooLong)))
                .andExpect(status().isBadRequest());
    }

    // AC-3: editing works; changing the team → 400 EPIC_TEAM_IMMUTABLE.
    @Test
    void updateAndTeamImmutability() throws Exception {
        String teamId = createTeam("Platform");
        String epicId = createEpic(teamId, "Infra");

        mockMvc.perform(put("/api/epics/" + epicId).with(user("u@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Infra v2\",\"description\":\"notes\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Infra v2"))
                .andExpect(jsonPath("$.description").value("notes"));

        mockMvc.perform(put("/api/epics/" + epicId).with(user("u@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Infra v2\",\"teamId\":\"%s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EPIC_TEAM_IMMUTABLE"));
    }

    // AC-4 + cross-module: an epic blocks team delete (409); deleting it frees the team (204).
    @Test
    void epicBlocksTeamDeleteUntilRemoved() throws Exception {
        String teamId = createTeam("Growth");
        String epicId = createEpic(teamId, "Onboarding");

        // The team now has a child epic → delete blocked.
        mockMvc.perform(delete("/api/teams/" + teamId).with(user("u@example.com")).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEAM_HAS_CHILDREN"));

        // Delete the (unreferenced) epic, then the team is deletable.
        mockMvc.perform(delete("/api/epics/" + epicId).with(user("u@example.com")).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/teams/" + teamId).with(user("u@example.com")).with(csrf()))
                .andExpect(status().isNoContent());
    }

    // FK/validation: creating an epic for a non-existent team → 404.
    @Test
    void createUnderMissingTeamReturns404() throws Exception {
        mockMvc.perform(post("/api/epics").param("teamId", UUID.randomUUID().toString())
                        .with(user("u@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"X\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
