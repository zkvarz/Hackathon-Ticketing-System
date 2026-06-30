package com.dataart.tickets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-context integration test against a real PostgreSQL via Testcontainers. Verifies the app
 * boots, Flyway applied the baseline, the schema holds only Flyway metadata (DoD-9), and the
 * health endpoint works.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BaselineIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    // Flyway applied the baseline: its history table exists with a successful V1.
    @Test
    void flywayBaselineApplied() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true AND version = '1'",
                Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    // DoD-9: a fresh database contains schema + Flyway metadata only — no preloaded
    // application data. (The schema itself grows as feature migrations land, e.g. `users`
    // from HTS-005; what must stay empty is application *data*.)
    @Test
    void freshDatabaseHasNoSeedData() {
        Integer userRows = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        assertThat(userRows).isZero();
    }

    // The app is up and the health endpoint responds.
    @Test
    void healthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
