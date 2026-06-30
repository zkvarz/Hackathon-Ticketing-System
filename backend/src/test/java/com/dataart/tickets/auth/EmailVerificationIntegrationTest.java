package com.dataart.tickets.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end email verification (HTS-007) against real Postgres + Mailpit via Testcontainers:
 * signup sends a captured email; verifying the link marks the user verified and consumes the
 * token; reuse is rejected; persisted expiry is issue+24h.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EmailVerificationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static GenericContainer<?> mailpit =
            new GenericContainer<>("axllent/mailpit:latest").withExposedPorts(1025, 8025);

    private static final Pattern TOKEN_IN_LINK = Pattern.compile("/verify\\?token=([A-Za-z0-9_-]+)");
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
    private EmailVerificationTokenRepository tokensRepo;

    private String mailpitBase() {
        return "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025);
    }

    private JsonNode getJson(String url) throws Exception {
        HttpResponse<String> res = HTTP.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return MAPPER.readTree(res.body());
    }

    @Test
    void signupSendsEmailThenVerifyConsumesTokenAndRejectsReuse() throws Exception {
        String email = "verify-me@example.com";

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password1\"}".formatted(email)))
                .andExpect(status().isCreated());

        // AC-1: exactly one message captured by Mailpit.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            JsonNode list = getJson(mailpitBase() + "/api/v1/messages");
            assertThat(list.get("total").asInt()).isEqualTo(1);
        });

        JsonNode list = getJson(mailpitBase() + "/api/v1/messages");
        String messageId = list.get("messages").get(0).get("ID").asText();
        JsonNode message = getJson(mailpitBase() + "/api/v1/message/" + messageId);
        String text = message.get("Text").asText();

        Matcher m = TOKEN_IN_LINK.matcher(text);
        assertThat(m.find()).as("verification link with token present in email").isTrue();
        String token = m.group(1);

        // AC-2: verifying a fresh token marks the user verified, consumes the token, no session.
        mockMvc.perform(get("/api/auth/verify").param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("verified"))
                .andExpect(jsonPath("$.email").value(email))
                // No auto-login (FR-A9): verification must not create a session. (An XSRF-TOKEN
                // cookie may be set by the CSRF filter — that's not a session.)
                .andExpect(cookie().doesNotExist("JSESSIONID"));

        assertThat(users.findByEmail(email).orElseThrow().isEmailVerified()).isTrue();
        EmailVerificationToken persisted = tokensRepo.findByToken(token).orElseThrow();
        assertThat(persisted.getConsumedAt()).isNotNull();

        // AC-4: persisted expiry is issue time + 24h.
        Duration ttl = Duration.between(persisted.getCreatedAt(), persisted.getExpiresAt());
        assertThat(ttl).isBetween(Duration.ofHours(24).minusSeconds(10), Duration.ofHours(24).plusSeconds(10));

        // AC-3: a consumed token cannot be reused.
        mockMvc.perform(get("/api/auth/verify").param("token", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }
}
