package com.dataart.tickets.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit slice test of the health endpoint — no database, no full context.
 */
// Security filters disabled for this slice — the health endpoint's public access is verified
// end-to-end in BaselineIntegrationTest; here we only test the controller behavior.
@WebMvcTest(HealthController.class)
@AutoConfigureMockMvc(addFilters = false)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Positive: GET /api/health returns 200 {"status":"UP"}.
    @Test
    void healthReturnsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // Negative: an unknown path under the API is not served by this controller (404).
    @Test
    void unknownPathReturns404() throws Exception {
        mockMvc.perform(get("/api/health/nope"))
                .andExpect(status().isNotFound());
    }
}
