package com.dataart.tickets.team;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Team CRUD over the API against real Postgres (HTS-015): create/list/rename/delete, the
 * case-insensitive unique name, and validation. Requests are authenticated with CSRF.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TeamCrudIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;

    private String createTeam(String name) throws Exception {
        String body = mockMvc.perform(post("/api/teams")
                        .with(user("u@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }

    // AC-1: create persists; list returns it (with zero counts and deletable).
    @Test
    void createThenListReturnsTeam() throws Exception {
        createTeam("Payments");

        mockMvc.perform(get("/api/teams").with(user("u@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Payments')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Payments')].epicCount").value(0))
                .andExpect(jsonPath("$[?(@.name == 'Payments')].ticketCount").value(0));
    }

    // AC-2: blank name → 400; case/space duplicate → 409 NAME_TAKEN.
    @Test
    void blankNameIs400AndDuplicateIs409() throws Exception {
        mockMvc.perform(post("/api/teams").with(user("u@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        createTeam("Billing");
        mockMvc.perform(post("/api/teams").with(user("u@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  billing \"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NAME_TAKEN"));
    }

    // AC-3: rename updates the name; AC-4: delete of an empty team succeeds, then 404.
    @Test
    void renameThenDeleteEmptyTeam() throws Exception {
        String id = createTeam("Platfrm");

        mockMvc.perform(put("/api/teams/" + id).with(user("u@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Platform\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Platform"));

        mockMvc.perform(delete("/api/teams/" + id).with(user("u@example.com")).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/teams/" + id).with(user("u@example.com")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
