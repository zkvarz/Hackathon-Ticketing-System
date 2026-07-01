package com.dataart.tickets.comment;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comment add + list over the API against real Postgres (HTS-023): chronological ordering,
 * author-from-principal, non-empty validation, the 404 for a missing ticket, and — critically —
 * that adding a comment does not advance the ticket's modified_at (FR-C5).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CommentIntegrationTest {

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

    private void addComment(String ticketId, String body) throws Exception {
        mockMvc.perform(post("/api/tickets/" + ticketId + "/comments").with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"%s\"}".formatted(body)))
                .andExpect(status().isCreated());
    }

    // AC-1 + AC-3: comments persist with author and list oldest-first.
    @Test
    void addTwoCommentsListedOldestFirst() throws Exception {
        String teamId = createTeam("Payments");
        String ticketId = createTicket(teamId).get("id").asText();

        addComment(ticketId, "First comment");
        addComment(ticketId, "Second comment");

        mockMvc.perform(get("/api/tickets/" + ticketId + "/comments").with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].body").value("First comment"))
                .andExpect(jsonPath("$[0].authorEmail").value(PRINCIPAL))
                .andExpect(jsonPath("$[1].body").value("Second comment"));
    }

    // AC-2: empty/whitespace body → 400.
    @Test
    void blankBodyRejected() throws Exception {
        String teamId = createTeam("Billing");
        String ticketId = createTicket(teamId).get("id").asText();

        mockMvc.perform(post("/api/tickets/" + ticketId + "/comments").with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // AC-4: adding a comment does not change the ticket's modified_at (FR-C5).
    @Test
    void addingCommentDoesNotBumpTicketModifiedAt() throws Exception {
        String teamId = createTeam("Platform");
        JsonNode ticket = createTicket(teamId);
        String ticketId = ticket.get("id").asText();

        String before = mockMvc.perform(get("/api/tickets/" + ticketId).with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String modifiedBefore = mapper.readTree(before).get("modifiedAt").asText();

        addComment(ticketId, "A comment should not touch modified_at");

        String after = mockMvc.perform(get("/api/tickets/" + ticketId).with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String modifiedAfter = mapper.readTree(after).get("modifiedAt").asText();

        assertThat(modifiedAfter).isEqualTo(modifiedBefore);
    }

    // Negative: commenting on a non-existent ticket → 404.
    @Test
    void commentOnMissingTicketReturns404() throws Exception {
        mockMvc.perform(post("/api/tickets/" + java.util.UUID.randomUUID() + "/comments")
                        .with(user(PRINCIPAL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"hi\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
