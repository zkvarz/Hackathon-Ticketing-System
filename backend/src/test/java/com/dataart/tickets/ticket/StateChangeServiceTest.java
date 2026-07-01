package com.dataart.tickets.ticket;

import com.dataart.tickets.auth.User;
import com.dataart.tickets.auth.UserRepository;
import com.dataart.tickets.common.NotFoundException;
import com.dataart.tickets.epic.EpicRepository;
import com.dataart.tickets.team.Team;
import com.dataart.tickets.team.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the state-change service (HTS-027, FR-K7/FR-B6). A valid change advances
 * modified_at (fixed clock); any-to-any is allowed; re-setting the same state still advances
 * modified_at (documented AMB-3 exception); a missing ticket → 404.
 */
@ExtendWith(MockitoExtension.class)
class StateChangeServiceTest {

    private static final Instant LATER = Instant.parse("2026-06-01T12:00:00Z");

    @Mock
    private TicketRepository tickets;
    @Mock
    private TeamRepository teams;
    @Mock
    private EpicRepository epics;
    @Mock
    private UserRepository users;
    @Mock
    private TicketActivityRepository activity;

    private TicketService service() {
        return new TicketService(tickets, teams, epics, users, activity, Clock.fixed(LATER, ZoneOffset.UTC));
    }

    private Ticket ticketInState(TicketState state) {
        Team team = new Team("Payments");
        Ticket ticket = new Ticket(team, null, TicketType.BUG, TicketState.NEW,
                "T", "B", new User("u@example.com", "h"));
        // Put the ticket in the requested starting state (modified_at starts unset here — in a unit
        // test no JPA-Auditing runs; changeState's explicit stamp is what these tests assert on).
        ticket.applyChanges(team, null, TicketType.BUG, state, "T", "B");
        return ticket;
    }

    // AC-1 + AC-3: any-to-any change persists and advances modified_at to the clock instant.
    @Test
    void changeStateAdvancesModifiedAt() {
        UUID id = UUID.randomUUID();
        Ticket ticket = ticketInState(TicketState.NEW);
        when(tickets.findById(id)).thenReturn(Optional.of(ticket));

        Ticket result = service().changeState(id, TicketState.DONE, "u@example.com");

        assertThat(result.getState()).isEqualTo(TicketState.DONE);
        assertThat(result.getModifiedAt()).isEqualTo(LATER);
    }

    // Boundary: re-setting the current state still advances modified_at (explicit change request).
    @Test
    void sameStateStillAdvancesModifiedAt() {
        UUID id = UUID.randomUUID();
        Ticket ticket = ticketInState(TicketState.IN_PROGRESS);
        when(tickets.findById(id)).thenReturn(Optional.of(ticket));

        Ticket result = service().changeState(id, TicketState.IN_PROGRESS, "u@example.com");

        assertThat(result.getState()).isEqualTo(TicketState.IN_PROGRESS);
        assertThat(result.getModifiedAt()).isEqualTo(LATER);
    }

    // Negative: state change on a non-existent ticket → 404.
    @Test
    void changeStateOnMissingTicketThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(tickets.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().changeState(id, TicketState.DONE, "u@example.com"))
                .isInstanceOf(NotFoundException.class);
    }
}
