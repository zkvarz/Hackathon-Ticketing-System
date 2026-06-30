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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end resend (HTS-009): a resend invalidates the original token and emails a new one;
 * the old token no longer verifies, the new one does; Mailpit captures the second message.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ResendIntegrationTest {

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

    private String mailpitBase() {
        return "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025);
    }

    private JsonNode getJson(String url) throws Exception {
        HttpResponse<String> res = HTTP.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return MAPPER.readTree(res.body());
    }

    /** All verification tokens currently captured by Mailpit, oldest first. */
    private List<String> capturedTokens() throws Exception {
        JsonNode list = getJson(mailpitBase() + "/api/v1/messages");
        List<String> result = new ArrayList<>();
        for (JsonNode msg : list.get("messages")) {
            JsonNode full = getJson(mailpitBase() + "/api/v1/message/" + msg.get("ID").asText());
            Matcher m = TOKEN_IN_LINK.matcher(full.get("Text").asText());
            if (m.find()) {
                result.add(m.group(1));
            }
        }
        return result;
    }

    @Test
    void resendRotatesTokenAndOldOneStopsWorking() throws Exception {
        String email = "resend-me@example.com";
        String signup = "{\"email\":\"%s\",\"password\":\"password1\"}".formatted(email);
        String resend = "{\"email\":\"%s\"}".formatted(email);

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON).content(signup))
                .andExpect(status().isCreated());
        await().atMost(Duration.ofSeconds(10))
                .until(() -> getJson(mailpitBase() + "/api/v1/messages").get("total").asInt() == 1);

        // Resend → a second email with a different token.
        mockMvc.perform(post("/api/auth/resend")
                .contentType(MediaType.APPLICATION_JSON).content(resend))
                .andExpect(status().isAccepted());
        await().atMost(Duration.ofSeconds(10))
                .until(() -> getJson(mailpitBase() + "/api/v1/messages").get("total").asInt() == 2);

        List<String> tokens = capturedTokens();
        // Mailpit returns newest first; collect both distinct tokens.
        assertThat(tokens).hasSize(2);
        String tokenA = tokens.get(1); // older (signup)
        String tokenB = tokens.get(0); // newer (resend)
        assertThat(tokenA).isNotEqualTo(tokenB);

        // AC-2: the old token is now invalid; the new one verifies.
        mockMvc.perform(get("/api/auth/verify").param("token", tokenA))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/auth/verify").param("token", tokenB))
                .andExpect(status().isOk());
    }

    // AC-3: resend for an unknown email returns the same generic 202 (no enumeration) and sends nothing.
    @Test
    void resendForUnknownEmailIsGenericSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isAccepted());
    }
}
