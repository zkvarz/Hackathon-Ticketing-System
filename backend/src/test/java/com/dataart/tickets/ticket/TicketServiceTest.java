package com.dataart.tickets.ticket;

import com.dataart.tickets.auth.User;
import com.dataart.tickets.auth.UserRepository;
import com.dataart.tickets.common.NotFoundException;
import com.dataart.tickets.epic.Epic;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ticket business logic (HTS-019). Covers positive create/update, reference
 * validation (404s), the {@code createdBy}-from-principal rule, and the AMB-3 diffing contract: a
 * real field change is applied and reported, an identical save is a no-op. The {@code modified_at}
 * value itself is owned by JPA Auditing (HTS-047) and asserted in the integration tests.
 */
@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

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
        return service(LATER);
    }

    private TicketService service(Instant now) {
        return new TicketService(tickets, teams, epics, users, activity, Clock.fixed(now, ZoneOffset.UTC));
    }

    // Positive: create trims title/body, defaults a missing state to NEW, and sets created_by from
    // the resolved principal — never from the client.
    @Test
    void createTrimsDefaultsStateAndSetsCreator() {
        UUID teamId = UUID.randomUUID();
        Team team = new Team("Payments");
        User creator = new User("u@example.com", "hash");
        when(teams.findById(teamId)).thenReturn(Optional.of(team));
        when(users.findByEmail("u@example.com")).thenReturn(Optional.of(creator));
        when(tickets.save(any(Ticket.class))).thenAnswer(i -> i.getArgument(0));

        Ticket ticket = service().create(teamId, null, TicketType.BUG, null,
                "  Broken login  ", "  Steps  ", "u@example.com");

        assertThat(ticket.getTitle()).isEqualTo("Broken login");
        assertThat(ticket.getBody()).isEqualTo("Steps");
        assertThat(ticket.getState()).isEqualTo(TicketState.NEW);
        assertThat(ticket.getType()).isEqualTo(TicketType.BUG);
        assertThat(ticket.getCreatedBy()).isSameAs(creator);
    }

    // Positive: create links an existing epic when supplied.
    @Test
    void createLinksExistingEpic() {
        UUID teamId = UUID.randomUUID();
        UUID epicId = UUID.randomUUID();
        Team team = new Team("Payments");
        Epic epic = new Epic(team, "Checkout", null);
        when(teams.findById(teamId)).thenReturn(Optional.of(team));
        when(epics.findById(epicId)).thenReturn(Optional.of(epic));
        when(users.findByEmail("u@example.com")).thenReturn(Optional.of(new User("u@example.com", "h")));
        when(tickets.save(any(Ticket.class))).thenAnswer(i -> i.getArgument(0));

        Ticket ticket = service().create(teamId, epicId, TicketType.FEATURE, TicketState.NEW,
                "Add card", "body", "u@example.com");

        assertThat(ticket.getEpic()).isSameAs(epic);
    }

    // Negative: create under a missing team → 404; nothing persisted.
    @Test
    void createUnderMissingTeamThrowsNotFound() {
        UUID teamId = UUID.randomUUID();
        when(teams.findById(teamId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(teamId, null, TicketType.BUG, TicketState.NEW,
                "T", "B", "u@example.com"))
                .isInstanceOf(NotFoundException.class);
        verify(tickets, never()).save(any());
    }

    // Negative: create with a non-existent epic reference → 404 (FR-K8).
    @Test
    void createWithMissingEpicThrowsNotFound() {
        UUID teamId = UUID.randomUUID();
        UUID epicId = UUID.randomUUID();
        when(teams.findById(teamId)).thenReturn(Optional.of(new Team("P")));
        when(epics.findById(epicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(teamId, epicId, TicketType.BUG, TicketState.NEW,
                "T", "B", "u@example.com"))
                .isInstanceOf(NotFoundException.class);
        verify(tickets, never()).save(any());
    }

    // Negative: get/update/delete of a non-existent ticket → 404.
    @Test
    void getMissingTicketThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(tickets.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().get(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteMissingTicketThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(tickets.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().delete(id)).isInstanceOf(NotFoundException.class);
        verify(tickets, never()).delete(any());
    }

    // AMB-3: a real field change is applied through the service (the modified_at advance itself is
    // JPA Auditing's job, verified in TicketCrudIntegrationTest).
    @Test
    void updateAppliesRealChange() {
        UUID id = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Team team = new Team("Payments");
        Ticket ticket = existingTicket(team);
        when(tickets.findById(id)).thenReturn(Optional.of(ticket));
        when(teams.findById(teamId)).thenReturn(Optional.of(team));

        Ticket result = service(LATER).update(id, teamId, null, TicketType.BUG, TicketState.IN_PROGRESS,
                "New title", "Body", "u@example.com");

        assertThat(result.getTitle()).isEqualTo("New title");
        assertThat(result.getState()).isEqualTo(TicketState.IN_PROGRESS);
    }

    // AMB-3: saving identical values leaves the entity untouched (no dirtying → auditing won't bump).
    @Test
    void updateWithIdenticalValuesIsNoOp() {
        UUID id = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Team team = new Team("Payments");
        Ticket ticket = existingTicket(team);
        when(tickets.findById(id)).thenReturn(Optional.of(ticket));
        when(teams.findById(teamId)).thenReturn(Optional.of(team));

        Ticket result = service(LATER).update(id, teamId, null, TicketType.BUG, TicketState.NEW,
                "Title", "Body", "u@example.com");

        assertThat(result.getTitle()).isEqualTo("Title");
        assertThat(result.getState()).isEqualTo(TicketState.NEW);
    }

    // The AMB-3 diffing contract at the entity level: applyChanges reports whether anything changed.
    @Test
    void applyChangesReportsChangeVsNoOp() {
        Team team = new Team("Payments");
        Ticket ticket = existingTicket(team); // title "Title", body "Body", BUG, NEW

        assertThat(ticket.applyChanges(team, null, TicketType.BUG, TicketState.NEW, "Title", "Body"))
                .as("identical values → no-op").isFalse();
        assertThat(ticket.applyChanges(team, null, TicketType.BUG, TicketState.DONE, "Title", "Body"))
                .as("state changed → real change").isTrue();
    }

    // A ticket with title "Title", body "Body", type BUG, state NEW, no epic.
    private Ticket existingTicket(Team team) {
        Ticket ticket = new Ticket(team, null, TicketType.BUG, TicketState.NEW,
                "seed", "seed", new User("u@example.com", "h"));
        ticket.applyChanges(team, null, TicketType.BUG, TicketState.NEW, "Title", "Body");
        return ticket;
    }
}
