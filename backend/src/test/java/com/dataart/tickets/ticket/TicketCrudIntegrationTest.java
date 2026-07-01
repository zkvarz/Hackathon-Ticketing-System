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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket CRUD over the API against real Postgres (HTS-019): create/read/update/delete round-trip,
 * server-side enum + field validation, created-by-from-principal, AMB-3 modified-at semantics,
 * UTC timestamps, the ticket→comment delete cascade (FR-K6), and the cross-module delete-blocks
 * a ticket places on its epic (FR-E8) and team (FR-T5).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TicketCrudIntegrationTest {

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
    @Autowired
    private JdbcTemplate jdbc;

    private UUID userId;

    @BeforeEach
    void seedUser() {
        // created_by is a real FK; the principal below must resolve to an existing user row.
        userId = users.findByEmail(PRINCIPAL)
                .orElseGet(() -> users.save(new User(PRINCIPAL, "hash")))
                .getId();
    }

    // ---- helpers -----------------------------------------------------------------------------

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

    private String ticketBody(String teamId, String epicId, String type, String state,
                              String title, String body) {
        String epicField = epicId == null ? "null" : "\"" + epicId + "\"";
        return """
                {"teamId":"%s","epicId":%s,"type":"%s","state":"%s","title":"%s","body":"%s"}
                """.formatted(teamId, epicField, type, state, title, body);
    }

    private JsonNode createTicket(String teamId, String epicId, String type, String state,
                                  String title, String body) throws Exception {
        String response = mockMvc.perform(post("/api/tickets").with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(teamId, epicId, type, state, title, body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    // ---- tests -------------------------------------------------------------------------------

    // AC-1 + AC-2: create persists all fields with server-set created-by/at; get returns them all.
    @Test
    void createThenGetReturnsAllFields() throws Exception {
        String teamId = createTeam("Payments");
        String epicId = createEpic(teamId, "Checkout");

        JsonNode created = createTicket(teamId, epicId, "bug", "new", "Broken login", "Steps to repro");
        String id = created.get("id").asText();

        assertThat(created.get("type").asText()).isEqualTo("bug");
        assertThat(created.get("state").asText()).isEqualTo("new");
        assertThat(created.get("epicId").asText()).isEqualTo(epicId);
        assertThat(created.get("createdBy").asText()).isEqualTo(userId.toString());
        assertThat(created.get("createdByEmail").asText()).isEqualTo(PRINCIPAL);
        assertThat(created.get("createdAt").asText()).isNotBlank();

        mockMvc.perform(get("/api/tickets/" + id).with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Broken login"))
                .andExpect(jsonPath("$.body").value("Steps to repro"))
                .andExpect(jsonPath("$.teamId").value(teamId))
                .andExpect(jsonPath("$.createdByEmail").value(PRINCIPAL));
    }

    // AC-1: invalid enum value is rejected server-side with 400 (FR-K8).
    @Test
    void invalidEnumReturns400() throws Exception {
        String teamId = createTeam("Billing");

        mockMvc.perform(post("/api/tickets").with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(teamId, null, "banana", "new", "X", "Y")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/tickets").with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(teamId, null, "bug", "archived", "X", "Y")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // AC-5: title/body non-empty and within limits; boundary values pass, over-length fails.
    @Test
    void titleAndBodyValidation() throws Exception {
        String teamId = createTeam("Platform");

        // Blank title → 400 with a field error.
        mockMvc.perform(post("/api/tickets").with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(teamId, null, "bug", "new", "   ", "body")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // Over-length title (201) → 400; over-length body (10001) → 400.
        mockMvc.perform(post("/api/tickets").with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(teamId, null, "bug", "new", "a".repeat(201), "body")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/tickets").with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(teamId, null, "bug", "new", "ok", "b".repeat(10001))))
                .andExpect(status().isBadRequest());

        // Boundary: title 200 + body 10000 → 201.
        createTicket(teamId, null, "bug", "new", "a".repeat(200), "b".repeat(10000));
    }

    // AC-3 (AMB-3): editing a field advances modified_at; saving unchanged values does not.
    @Test
    void modifiedAtAdvancesOnlyOnRealChange() throws Exception {
        String teamId = createTeam("Growth");
        String id = createTicket(teamId, null, "bug", "new", "Title", "Body").get("id").asText();

        // Read the persisted value back so the baseline has the same (microsecond) precision as
        // subsequent DB round-trips — the create response still carries the in-memory nanoseconds.
        String fetched = mockMvc.perform(get("/api/tickets/" + id).with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Instant originalModified = Instant.parse(mapper.readTree(fetched).get("modifiedAt").asText());

        // No-op update: same values → modified_at unchanged.
        String unchanged = mockMvc.perform(put("/api/tickets/" + id).with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(teamId, null, "bug", "new", "Title", "Body")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(Instant.parse(mapper.readTree(unchanged).get("modifiedAt").asText()))
                .isEqualTo(originalModified);

        // Real change: state moves → modified_at advances.
        String changed = mockMvc.perform(put("/api/tickets/" + id).with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(teamId, null, "bug", "in_progress", "Title", "Body")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("in_progress"))
                .andReturn().getResponse().getContentAsString();
        assertThat(Instant.parse(mapper.readTree(changed).get("modifiedAt").asText()))
                .isAfter(originalModified);
    }

    // AC-4 + FR-K6: deleting a ticket removes its comment rows (DB cascade).
    @Test
    void deleteCascadesComments() throws Exception {
        String teamId = createTeam("Support");
        JsonNode created = createTicket(teamId, null, "feature", "new", "Has comments", "Body");
        UUID ticketId = UUID.fromString(created.get("id").asText());

        // Seed a comment directly (the comment API lands in HTS-023; the cascade is a ticket concern).
        jdbc.update("INSERT INTO comments (id, ticket_id, author_id, body, created_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), ticketId, userId, "Nice catch", Timestamp.from(Instant.now()));
        Long before = jdbc.queryForObject(
                "SELECT count(*) FROM comments WHERE ticket_id = ?", Long.class, ticketId);
        assertThat(before).isEqualTo(1L);

        mockMvc.perform(delete("/api/tickets/" + ticketId).with(user(PRINCIPAL)).with(csrf()))
                .andExpect(status().isNoContent());

        Long after = jdbc.queryForObject(
                "SELECT count(*) FROM comments WHERE ticket_id = ?", Long.class, ticketId);
        assertThat(after).isZero();
        mockMvc.perform(get("/api/tickets/" + ticketId).with(user(PRINCIPAL)))
                .andExpect(status().isNotFound());
    }

    // AC-4: deleting a non-existent ticket → 404.
    @Test
    void deleteMissingReturns404() throws Exception {
        mockMvc.perform(delete("/api/tickets/" + UUID.randomUUID()).with(user(PRINCIPAL)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // Cross-module: a ticket blocks deletion of its epic (409) and its team (409) until removed.
    @Test
    void ticketBlocksEpicAndTeamDelete() throws Exception {
        String teamId = createTeam("Delivery");
        String epicId = createEpic(teamId, "Rollout");
        JsonNode created = createTicket(teamId, epicId, "fix", "new", "Blocker", "Body");
        String ticketId = created.get("id").asText();

        mockMvc.perform(delete("/api/epics/" + epicId).with(user(PRINCIPAL)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EPIC_HAS_TICKETS"));
        mockMvc.perform(delete("/api/teams/" + teamId).with(user(PRINCIPAL)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEAM_HAS_CHILDREN"));

        // Removing the ticket frees both.
        mockMvc.perform(delete("/api/tickets/" + ticketId).with(user(PRINCIPAL)).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/epics/" + epicId).with(user(PRINCIPAL)).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/teams/" + teamId).with(user(PRINCIPAL)).with(csrf()))
                .andExpect(status().isNoContent());
    }

    // FR-P5: timestamps are serialized as UTC ISO-8601 (trailing 'Z').
    @Test
    void timestampsAreUtcIso8601() throws Exception {
        String teamId = createTeam("Timezone");
        JsonNode created = createTicket(teamId, null, "bug", "new", "When", "Body");

        assertThat(created.get("createdAt").asText()).endsWith("Z");
        assertThat(created.get("modifiedAt").asText()).endsWith("Z");
    }

    // FR-B7: board listing is ordered most-recently-modified first.
    @Test
    void listByTeamOrdersByMostRecentlyModified() throws Exception {
        String teamId = createTeam("Ordering");
        JsonNode first = createTicket(teamId, null, "bug", "new", "First", "Body");
        JsonNode second = createTicket(teamId, null, "bug", "new", "Second", "Body");

        // Touch the first ticket so it becomes the most recently modified.
        mockMvc.perform(put("/api/tickets/" + first.get("id").asText()).with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(teamId, null, "bug", "in_progress", "First", "Body")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tickets").param("teamId", teamId).with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("First"))
                .andExpect(jsonPath("$[1].title").value("Second"));
    }
}
