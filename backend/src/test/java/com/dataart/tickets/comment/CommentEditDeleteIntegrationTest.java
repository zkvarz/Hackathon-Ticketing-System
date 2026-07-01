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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Author edit/delete of own comments over the API against real Postgres (HTS-039): the author edits
 * (body updates, edited_at set) and deletes; a different user is blocked with 403; and editing a
 * comment does not advance the ticket's modified_at (FR-C5 preserved).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CommentEditDeleteIntegrationTest {

    private static final String AUTHOR = "author@example.com";
    private static final String OTHER = "other@example.com";

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
    void seedUsers() {
        users.findByEmail(AUTHOR).orElseGet(() -> users.save(new User(AUTHOR, "hash")));
    }

    private String createTeam(String name) throws Exception {
        String body = mockMvc.perform(post("/api/teams").with(user(AUTHOR)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }

    private JsonNode createTicket(String teamId) throws Exception {
        String body = mockMvc.perform(post("/api/tickets").with(user(AUTHOR)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":\"%s\",\"type\":\"bug\",\"state\":\"new\",\"title\":\"T\",\"body\":\"B\"}"
                                .formatted(teamId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    private String addComment(String ticketId, String body) throws Exception {
        String res = mockMvc.perform(post("/api/tickets/" + ticketId + "/comments")
                        .with(user(AUTHOR)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"%s\"}".formatted(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).get("id").asText();
    }

    // AC-1: the author edits — body updates and edited_at is set.
    @Test
    void authorEditsCommentBodyAndEditedAtSet() throws Exception {
        String ticketId = createTicket(createTeam("Payments")).get("id").asText();
        String commentId = addComment(ticketId, "Original");

        mockMvc.perform(put("/api/tickets/" + ticketId + "/comments/" + commentId)
                        .with(user(AUTHOR)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"  Edited  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Edited"))
                .andExpect(jsonPath("$.editedAt").isNotEmpty());
    }

    // AC-2: the author deletes — the comment disappears from the list.
    @Test
    void authorDeletesComment() throws Exception {
        String ticketId = createTicket(createTeam("Billing")).get("id").asText();
        String commentId = addComment(ticketId, "Delete me");

        mockMvc.perform(delete("/api/tickets/" + ticketId + "/comments/" + commentId)
                        .with(user(AUTHOR)).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tickets/" + ticketId + "/comments").with(user(AUTHOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // AC-3: a non-author cannot edit or delete → 403; the comment is unchanged.
    @Test
    void nonAuthorCannotEditOrDelete() throws Exception {
        String ticketId = createTicket(createTeam("Platform")).get("id").asText();
        String commentId = addComment(ticketId, "Owned by author");

        mockMvc.perform(put("/api/tickets/" + ticketId + "/comments/" + commentId)
                        .with(user(OTHER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"hijack\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(delete("/api/tickets/" + ticketId + "/comments/" + commentId)
                        .with(user(OTHER)).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // Still present and unedited.
        mockMvc.perform(get("/api/tickets/" + ticketId + "/comments").with(user(AUTHOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].body").value("Owned by author"))
                .andExpect(jsonPath("$[0].editedAt").doesNotExist());
    }

    // AC-4: editing a comment does not advance the ticket's modified_at (FR-C5).
    @Test
    void editingCommentDoesNotBumpTicketModifiedAt() throws Exception {
        String ticketId = createTicket(createTeam("Infra")).get("id").asText();
        String commentId = addComment(ticketId, "Original");

        String before = mockMvc.perform(get("/api/tickets/" + ticketId).with(user(AUTHOR)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String modifiedBefore = mapper.readTree(before).get("modifiedAt").asText();

        mockMvc.perform(put("/api/tickets/" + ticketId + "/comments/" + commentId)
                        .with(user(AUTHOR)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Edited\"}"))
                .andExpect(status().isOk());

        String after = mockMvc.perform(get("/api/tickets/" + ticketId).with(user(AUTHOR)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String modifiedAfter = mapper.readTree(after).get("modifiedAt").asText();

        assertThat(modifiedAfter).isEqualTo(modifiedBefore);
    }

    // Negative: editing a missing comment → 404.
    @Test
    void editMissingCommentReturns404() throws Exception {
        String ticketId = createTicket(createTeam("Search")).get("id").asText();

        mockMvc.perform(put("/api/tickets/" + ticketId + "/comments/" + java.util.UUID.randomUUID())
                        .with(user(AUTHOR)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
