package com.dataart.tickets.common;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end error-model checks over real Postgres (HTS-031). Drives genuine failures through
 * the API and asserts they all surface as the standard {@link ApiError} shape with the right
 * status + stable code: a 409 conflict, a 404 not-found, a missing required param, an unparseable
 * enum, and an unknown route.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ErrorHandlingIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    private static final String USER = "u@example.com";

    // Conflict: a duplicate team name → 409 NAME_TAKEN in the model.
    @Test
    void duplicateTeamNameIs409() throws Exception {
        mockMvc.perform(post("/api/teams").with(user(USER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Errbox\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/teams").with(user(USER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"errbox\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NAME_TAKEN"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // Not-found: fetching a random ticket id → 404 NOT_FOUND in the model.
    @Test
    void missingTicketIs404() throws Exception {
        mockMvc.perform(get("/api/tickets/" + UUID.randomUUID()).with(user(USER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // Missing required param: the board list needs teamId → 400 with the param named.
    @Test
    void missingTeamIdParamIs400WithFieldError() throws Exception {
        mockMvc.perform(get("/api/tickets").with(user(USER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("teamId"));
    }

    // Unparseable enum in the body → 400 VALIDATION_FAILED (no leakage of the raw value).
    @Test
    void invalidEnumBodyIs400() throws Exception {
        String body = """
                {"teamId":"%s","type":"banana","state":"new","title":"t","body":"b"}
                """.formatted(UUID.randomUUID());
        mockMvc.perform(post("/api/tickets").with(user(USER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // Unknown route (authenticated, so it passes security and reaches the dispatcher) → 404 in
    // the model rather than Boot's Whitelabel page.
    @Test
    void unknownRouteIs404() throws Exception {
        mockMvc.perform(get("/api/does-not-exist").with(user(USER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
