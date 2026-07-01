package com.dataart.tickets.ticket;

import com.dataart.tickets.auth.User;
import com.dataart.tickets.auth.UserRepository;
import com.dataart.tickets.team.Team;
import com.dataart.tickets.team.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTS-044 — trigram GIN index for the board title substring search. Verifies two things against
 * real Postgres: (1) the {@code ?q=} search still returns exactly the case-insensitive substring
 * matches (no semantic change vs. HTS-029), and (2) the planner uses {@code ix_tickets_title_trgm}
 * for a leading-wildcard substring query. A b-tree cannot serve {@code LIKE '%…%'}, so ruling out
 * a seq scan and still seeing the trigram index in the plan proves the trigram path works.
 */
@SpringBootTest
@Testcontainers
class TrigramSearchIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TicketRepository tickets;
    @Autowired
    private TeamRepository teams;
    @Autowired
    private UserRepository users;
    @Autowired
    private DataSource dataSource;

    private UUID teamId;
    private int expectedPaymentMatches;

    @BeforeEach
    void seed() {
        tickets.deleteAll();
        User creator = users.findByEmail("trg@example.com")
                .orElseGet(() -> users.save(new User("trg@example.com", "$argon2id$hash")));
        Team team = teams.save(new Team("Trigram " + UUID.randomUUID()));
        teamId = team.getId();

        // A few hundred rows so the table is beyond a toy size; ~20% contain "payment" in mixed case.
        List<Ticket> batch = new ArrayList<>();
        expectedPaymentMatches = 0;
        for (int i = 0; i < 400; i++) {
            String title;
            if (i % 10 == 0) {
                title = "Fix PAYMENT gateway " + i;
                expectedPaymentMatches++;
            } else if (i % 10 == 5) {
                title = "Add payment retry " + i;
                expectedPaymentMatches++;
            } else {
                title = "Routine maintenance item " + i;
            }
            batch.add(new Ticket(team, null, TicketType.BUG, TicketState.NEW, title, "body", creator));
        }
        tickets.saveAll(batch);
    }

    // AC-2: results identical to a plain case-insensitive substring reference (no semantic change).
    @Test
    void searchReturnsSameResultsAsReference() {
        List<Ticket> hits = tickets.search(teamId, null, null, "payment");

        assertThat(hits).hasSize(expectedPaymentMatches);
        assertThat(hits).allSatisfy(t ->
                assertThat(t.getTitle().toLowerCase()).contains("payment"));
    }

    // AC-1 + AC-3: the trigram index exists and the planner uses it for a leading-wildcard search.
    @Test
    void substringSearchUsesTrigramIndex() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("ANALYZE tickets");
            // Penalize a seq scan so the planner must use an available index if the query supports
            // one — a b-tree can't serve `%…%`, so the only candidate is the trigram GIN index.
            st.execute("SET enable_seqscan = off");

            StringBuilder plan = new StringBuilder();
            try (ResultSet rs = st.executeQuery(
                    "EXPLAIN (FORMAT JSON) SELECT id FROM tickets "
                            + "WHERE lower(title) LIKE lower('%payment%')")) {
                while (rs.next()) {
                    plan.append(rs.getString(1));
                }
            }

            assertThat(plan.toString()).contains("ix_tickets_title_trgm");
        }
    }
}
