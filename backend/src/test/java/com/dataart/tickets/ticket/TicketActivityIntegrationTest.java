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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket activity history over the API against real Postgres (HTS-041). Exercises the full sequence
 * create → edit → state-change and asserts the append-only log is accurate, actor-attributed, and
 * chronological (AC-1/AC-2); that a no-op edit and a failed (rolled-back) update write nothing
 * (AC-3 + transactional consistency); and that history for a missing ticket → 404.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TicketActivityIntegrationTest {

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

    private String createTicket(String teamId) throws Exception {
        String body = mockMvc.perform(post("/api/tickets").with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":\"%s\",\"type\":\"bug\",\"state\":\"new\",\"title\":\"T\",\"body\":\"B\"}"
                                .formatted(teamId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }

    private JsonNode activity(String ticketId) throws Exception {
        String body = mockMvc.perform(get("/api/tickets/" + ticketId + "/activity").with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    // AC-1: creating a ticket appends exactly one "created" entry, attributed to the actor.
    @Test
    void createAppendsCreatedEntry() throws Exception {
        String teamId = createTeam("Alpha");
        String id = createTicket(teamId);

        JsonNode log = activity(id);
        assertThat(log).hasSize(1);
        assertThat(log.get(0).get("field").asText()).isEqualTo("created");
        assertThat(log.get(0).get("actorEmail").asText()).isEqualTo(PRINCIPAL);
        assertThat(log.get(0).get("oldValue").isNull()).isTrue();
        assertThat(log.get(0).get("newValue").isNull()).isTrue();
    }

    // AC-1/AC-2: create → edit (type + title) → state-change yields an accurate, chronological log
    // with one row per changed field, each carrying actor + old/new + timestamp.
    @Test
    void fullSequenceIsRecordedChronologically() throws Exception {
        String teamId = createTeam("Beta");
        String id = createTicket(teamId);

        // Edit: change type bug->feature and title T->"Renamed" (body/team/epic/state unchanged).
        mockMvc.perform(put("/api/tickets/" + id).with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"teamId\":\"%s\",\"type\":\"feature\",\"state\":\"new\","
                                + "\"title\":\"Renamed\",\"body\":\"B\"}").formatted(teamId)))
                .andExpect(status().isOk());

        // State change new -> in_progress.
        mockMvc.perform(patch("/api/tickets/" + id + "/state").with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"state\":\"in_progress\"}"))
                .andExpect(status().isOk());

        JsonNode log = activity(id);
        assertThat(log).hasSize(4);
        // Field-order within the edit is team, epic, type, state, title, body → here [type, title].
        assertThat(log.get(0).get("field").asText()).isEqualTo("created");
        assertThat(log.get(1).get("field").asText()).isEqualTo("type");
        assertThat(log.get(1).get("oldValue").asText()).isEqualTo("bug");
        assertThat(log.get(1).get("newValue").asText()).isEqualTo("feature");
        assertThat(log.get(2).get("field").asText()).isEqualTo("title");
        assertThat(log.get(2).get("oldValue").asText()).isEqualTo("T");
        assertThat(log.get(2).get("newValue").asText()).isEqualTo("Renamed");
        assertThat(log.get(3).get("field").asText()).isEqualTo("state");
        assertThat(log.get(3).get("oldValue").asText()).isEqualTo("new");
        assertThat(log.get(3).get("newValue").asText()).isEqualTo("in_progress");
        // Every entry is attributed to the acting principal.
        for (JsonNode entry : log) {
            assertThat(entry.get("actorEmail").asText()).isEqualTo(PRINCIPAL);
            assertThat(entry.get("at").asText()).isNotBlank();
        }
    }

    // AC-3: a no-op edit (identical values) records nothing beyond the original creation entry.
    @Test
    void noOpEditRecordsNothing() throws Exception {
        String teamId = createTeam("Gamma");
        String id = createTicket(teamId);

        mockMvc.perform(put("/api/tickets/" + id).with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"teamId\":\"%s\",\"type\":\"bug\",\"state\":\"new\","
                                + "\"title\":\"T\",\"body\":\"B\"}").formatted(teamId)))
                .andExpect(status().isOk());

        assertThat(activity(id)).hasSize(1);
    }

    // Transactional consistency: a failed update (missing epic → 404) rolls back and writes no
    // activity — the log still shows only the creation entry.
    @Test
    void failedUpdateWritesNoActivity() throws Exception {
        String teamId = createTeam("Delta");
        String id = createTicket(teamId);

        mockMvc.perform(put("/api/tickets/" + id).with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"teamId\":\"%s\",\"epicId\":\"%s\",\"type\":\"feature\","
                                + "\"state\":\"done\",\"title\":\"X\",\"body\":\"Y\"}")
                                .formatted(teamId, UUID.randomUUID())))
                .andExpect(status().isNotFound());

        assertThat(activity(id)).hasSize(1);
    }

    // AC-2: activity for a non-existent ticket → 404.
    @Test
    void activityForMissingTicketReturns404() throws Exception {
        mockMvc.perform(get("/api/tickets/" + UUID.randomUUID() + "/activity").with(user(PRINCIPAL)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
