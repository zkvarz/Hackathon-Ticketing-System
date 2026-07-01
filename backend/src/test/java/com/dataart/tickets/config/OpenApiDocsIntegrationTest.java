package com.dataart.tickets.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OpenAPI docs (HTS-050). Verifies the springdoc endpoint is served, is reachable <em>without
 * authentication</em> (the SecurityConfig allowlist, since docs are enabled in the test/dev
 * profile), and documents both the domain DTOs and the standardized {@link
 * com.dataart.tickets.common.ApiError} model. As a side effect it writes the live spec to
 * {@code target/openapi.json} — the artifact the frontend's {@code npm run gen:api} turns into TS
 * types (so codegen can run from the build output without a running server).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OpenApiDocsIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsAreServedPubliclyAndDocumentTheContract() throws Exception {
        // No .with(user(...)) — an unauthenticated GET must succeed (public allowlist while enabled).
        String spec = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Domain DTOs are introspected from the controllers…
        assertThat(spec)
                .contains("\"openapi\"")
                .contains("TicketResponse")
                .contains("TeamResponse")
                .contains("EpicResponse")
                .contains("CommentResponse")
                // …and the standardized error model is registered by OpenApiConfig.
                .contains("ApiError")
                .contains("FieldError")
                // Enum wire values come through @JsonValue (lowercase).
                .contains("in_progress");

        // Publish the spec for the frontend type generator (HTS-050 FE side).
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target", "openapi.json"), spec);
    }

    @Test
    void swaggerUiIsServed() throws Exception {
        // The UI entry point redirects to the bundled index; either a redirect or 200 is fine.
        int statusCode = mockMvc.perform(get("/swagger-ui/index.html"))
                .andReturn().getResponse().getStatus();
        assertThat(statusCode).isLessThan(400);
    }
}
