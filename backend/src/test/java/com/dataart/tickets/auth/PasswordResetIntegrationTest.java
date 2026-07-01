package com.dataart.tickets.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end password reset (HTS-037) against real Postgres + Mailpit via Testcontainers:
 * forgot-password sends a captured email; the emailed token resets the password so the new one
 * logs in and the old one is rejected; reusing the token is rejected; and forgot-password for an
 * unknown email returns the same generic 202 (no enumeration).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PasswordResetIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static GenericContainer<?> mailpit =
            new GenericContainer<>("axllent/mailpit:latest").withExposedPorts(1025, 8025);

    private static final Pattern TOKEN_IN_LINK =
            Pattern.compile("/reset-password\\?token=([A-Za-z0-9_-]+)");
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", mailpit::getHost);
        registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String mailpitBase() {
        return "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025);
    }

    private JsonNode getJson(String url) throws Exception {
        HttpResponse<String> res = HTTP.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return MAPPER.readTree(res.body());
    }

    private void seedVerifiedUser(String email, String rawPassword) {
        User user = new User(email, passwordEncoder.encode(rawPassword));
        user.setEmailVerified(true);
        users.save(user);
    }

    private void login(String email, String password, int expectedStatus, String expectedCode)
            throws Exception {
        var result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().is(expectedStatus));
        if (expectedCode != null) {
            result.andExpect(jsonPath("$.code").value(expectedCode));
        }
    }

    @Test
    void forgotThenResetChangesPasswordAndRejectsReuse() throws Exception {
        String email = "reset-me@example.com";
        seedVerifiedUser(email, "old-password1");

        // AC-1: forgot-password issues a token + sends exactly one email.
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("sent"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            JsonNode list = getJson(mailpitBase() + "/api/v1/messages");
            assertThat(list.get("total").asInt()).isEqualTo(1);
        });

        JsonNode list = getJson(mailpitBase() + "/api/v1/messages");
        String messageId = list.get("messages").get(0).get("ID").asText();
        JsonNode message = getJson(mailpitBase() + "/api/v1/message/" + messageId);
        Matcher m = TOKEN_IN_LINK.matcher(message.get("Text").asText());
        assertThat(m.find()).as("reset link with token present in email").isTrue();
        String token = m.group(1);

        // AC-2: reset with the token succeeds.
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"password\":\"new-password1\"}".formatted(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("reset"));

        // AC-2: the old password no longer works; the new one does.
        login(email, "old-password1", 401, "BAD_CREDENTIALS");
        login(email, "new-password1", 200, null);

        // AC-3: the token is single-use — reuse is rejected.
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"password\":\"another-pass1\"}".formatted(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    // AC-4: forgot-password for an unknown email returns the same generic 202 and sends no email.
    @Test
    void forgotPasswordForUnknownEmailIsGenericAndSendsNothing() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("sent"));

        // No message is ever produced for a non-existent account. Scope to this recipient via
        // Mailpit search, since the container is shared across tests in this class.
        String url = mailpitBase() + "/api/v1/search?query=" +
                java.net.URLEncoder.encode("to:nobody@example.com", java.nio.charset.StandardCharsets.UTF_8);
        JsonNode search = getJson(url);
        assertThat(search.get("messages_count").asInt()).isZero();
    }
}
