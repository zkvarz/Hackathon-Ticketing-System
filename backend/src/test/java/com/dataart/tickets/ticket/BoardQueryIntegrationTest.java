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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Board query over the API against real Postgres (HTS-025, FR-B1/B2/B7/B10): team scoping,
 * most-recently-modified ordering, the board-card fields (incl. epic title), and a 100-ticket
 * sanity pass over the indexed query.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BoardQueryIntegrationTest {

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

    private JsonNode createTicket(String teamId, String epicId, String type, String state, String title)
            throws Exception {
        String epicField = epicId == null ? "null" : "\"" + epicId + "\"";
        String content = """
                {"teamId":"%s","epicId":%s,"type":"%s","state":"%s","title":"%s","body":"B"}
                """.formatted(teamId, epicField, type, state, title);
        String body = mockMvc.perform(post("/api/tickets").with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    // AC-1: only the requested team's tickets are returned.
    @Test
    void returnsOnlyRequestedTeamsTickets() throws Exception {
        String teamA = createTeam("Alpha");
        String teamB = createTeam("Beta");
        createTicket(teamA, null, "bug", "new", "A-one");
        createTicket(teamB, null, "bug", "new", "B-one");

        mockMvc.perform(get("/api/tickets").param("teamId", teamA).with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("A-one"));
    }

    // AC-2: ordered by modified_at descending — a touched ticket floats to the top.
    @Test
    void orderedByMostRecentlyModified() throws Exception {
        String teamId = createTeam("Ordering");
        String first = createTicket(teamId, null, "bug", "new", "First").get("id").asText();
        createTicket(teamId, null, "bug", "new", "Second");
        createTicket(teamId, null, "bug", "new", "Third");

        // Touch "First" so it becomes most recently modified.
        mockMvc.perform(put("/api/tickets/" + first).with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":\"%s\",\"type\":\"bug\",\"state\":\"in_progress\",\"title\":\"First\",\"body\":\"B\"}"
                                .formatted(teamId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tickets").param("teamId", teamId).with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("First"))
                .andExpect(jsonPath("$[1].title").value("Third"))
                .andExpect(jsonPath("$[2].title").value("Second"));
    }

    // AC-3: the response carries the fields the board card needs, including epic title.
    @Test
    void includesBoardCardFields() throws Exception {
        String teamId = createTeam("Cards");
        String epicId = createEpic(teamId, "Checkout");
        createTicket(teamId, epicId, "feature", "ready_for_implementation", "Card ticket");

        mockMvc.perform(get("/api/tickets").param("teamId", teamId).with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Card ticket"))
                .andExpect(jsonPath("$[0].type").value("feature"))
                .andExpect(jsonPath("$[0].state").value("ready_for_implementation"))
                .andExpect(jsonPath("$[0].epicId").value(epicId))
                .andExpect(jsonPath("$[0].epicTitle").value("Checkout"))
                .andExpect(jsonPath("$[0].modifiedAt").exists());
    }

    // Missing teamId → 400.
    @Test
    void missingTeamIdReturns400() throws Exception {
        mockMvc.perform(get("/api/tickets").with(user(PRINCIPAL)))
                .andExpect(status().isBadRequest());
    }

    // AC-4: 100+ tickets on one team board return in full over the indexed query.
    @Test
    void handlesOneHundredTickets() throws Exception {
        String teamId = createTeam("Scale");
        String[] states = {"new", "ready_for_implementation", "in_progress", "ready_for_acceptance", "done"};
        for (int i = 0; i < 100; i++) {
            createTicket(teamId, null, "bug", states[i % states.length], "Ticket " + i);
        }

        mockMvc.perform(get("/api/tickets").param("teamId", teamId).with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(100));
    }
}
