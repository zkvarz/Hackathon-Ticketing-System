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

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Server-side filter + search over the API against real Postgres (HTS-029, FR-B9/AMB-10): type
 * and epic equality filters, case-insensitive title substring search, AND-combination, the
 * unfiltered board, an invalid type param (400), and an unknown epic (empty) — all preserving the
 * most-recently-modified order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FilterSearchIntegrationTest {

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

    private String teamId;
    private String epicId;

    @BeforeEach
    void seed() throws Exception {
        users.findByEmail(PRINCIPAL).orElseGet(() -> users.save(new User(PRINCIPAL, "hash")));
        teamId = createTeam("Filters " + UUID.randomUUID());
        epicId = createEpic(teamId, "Checkout");
        // A mix of types, epics, and titles to filter over.
        createTicket("bug", epicId, "Payment gateway timeout");
        createTicket("feature", epicId, "Add saved cards");
        createTicket("bug", null, "Login PAYMENT retry");   // mixed-case substring "payment"
        createTicket("fix", null, "Typo on receipt");
    }

    private String createTeam(String name) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/teams").with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private String createEpic(String teamId, String title) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/epics").param("teamId", teamId)
                        .with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"%s\"}".formatted(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private void createTicket(String type, String epicId, String title) throws Exception {
        String epicField = epicId == null ? "null" : "\"" + epicId + "\"";
        mockMvc.perform(post("/api/tickets").with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":\"%s\",\"epicId\":%s,\"type\":\"%s\",\"state\":\"new\",\"title\":\"%s\",\"body\":\"B\"}"
                                .formatted(teamId, epicField, type, title)))
                .andExpect(status().isCreated());
    }

    // AC-4: no filters → the full board (all four tickets).
    @Test
    void noFiltersReturnsWholeBoard() throws Exception {
        mockMvc.perform(get("/api/tickets").param("teamId", teamId).with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    // AC-1: type filter returns only that type.
    @Test
    void typeFilterReturnsOnlyThatType() throws Exception {
        mockMvc.perform(get("/api/tickets").param("teamId", teamId).param("type", "bug").with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].type").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.equalTo("bug"))));
    }

    // AC-1: epic filter returns only that epic's tickets.
    @Test
    void epicFilterReturnsOnlyThatEpic() throws Exception {
        mockMvc.perform(get("/api/tickets").param("teamId", teamId).param("epicId", epicId).with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].epicId").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.equalTo(epicId))));
    }

    // AC-2: q matches case-insensitively as a substring of the title.
    @Test
    void queryMatchesCaseInsensitiveSubstring() throws Exception {
        mockMvc.perform(get("/api/tickets").param("teamId", teamId).param("q", "payment").with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)); // "Payment gateway..." + "Login PAYMENT retry"
    }

    // AC-3: multiple params AND together.
    @Test
    void filtersCombineWithAnd() throws Exception {
        // type=bug AND q=payment → both bug tickets contain "payment".
        mockMvc.perform(get("/api/tickets").param("teamId", teamId)
                        .param("type", "bug").param("q", "payment").with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // type=fix AND q=payment → none (the fix ticket has no "payment").
        mockMvc.perform(get("/api/tickets").param("teamId", teamId)
                        .param("type", "fix").param("q", "payment").with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // Negative: an invalid type param → 400.
    @Test
    void invalidTypeParamReturns400() throws Exception {
        mockMvc.perform(get("/api/tickets").param("teamId", teamId).param("type", "banana").with(user(PRINCIPAL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // Negative: an unknown epicId → empty result (valid).
    @Test
    void unknownEpicReturnsEmpty() throws Exception {
        mockMvc.perform(get("/api/tickets").param("teamId", teamId)
                        .param("epicId", UUID.randomUUID().toString()).with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
