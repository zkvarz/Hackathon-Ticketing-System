package com.dataart.tickets.auth;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for sign-up (HTS-005) against a real PostgreSQL via Testcontainers:
 * persistence, the case-insensitive unique index, and Argon2id hash storage.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SignUpIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    private static String body(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    // AC-1/AC-2: valid signup persists an unverified user; response omits the hash; hash is Argon2id.
    @Test
    void signupPersistsUnverifiedUserWithArgon2Hash() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("alice@example.com", "password1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User saved = users.findByEmail("alice@example.com").orElseThrow();
        assertThat(saved.isEmailVerified()).isFalse();
        assertThat(saved.getPasswordHash()).startsWith("$argon2");
        assertThat(saved.getPasswordHash()).isNotEqualTo("password1");
    }

    // AC-3: a second signup differing only by case/whitespace collides (case-insensitive unique).
    @Test
    void duplicateEmailCaseInsensitiveReturns409() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("bob@example.com", "password1")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("  BOB@Example.com ", "different1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));

        assertThat(users.findAll().stream()
                .filter(u -> u.getEmail().equals("bob@example.com"))
                .count()).isEqualTo(1);
    }

    // AC-4: invalid password length is rejected with 400 + field errors.
    @Test
    void shortPasswordReturns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("carol@example.com", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')]").exists());
    }
}
