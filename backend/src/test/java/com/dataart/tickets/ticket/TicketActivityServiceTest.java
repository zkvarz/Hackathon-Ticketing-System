package com.dataart.tickets.ticket;

import com.dataart.tickets.auth.User;
import com.dataart.tickets.auth.UserRepository;
import com.dataart.tickets.epic.EpicRepository;
import com.dataart.tickets.team.Team;
import com.dataart.tickets.team.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ticket activity recording (HTS-041). Verifies that create/edit/state-change append
 * accurate append-only rows: creation logs one {@code created} entry (AC-1); a multi-field edit logs
 * exactly one row per changed field with the others excluded (AC-2/boundary); a no-op edit and a
 * re-set of the same state log nothing (AC-3/negative). Rows are captured from the mocked repository.
 */
@ExtendWith(MockitoExtension.class)
class TicketActivityServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

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
        return new TicketService(tickets, teams, epics, users, activity, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Ticket existing(Team team) {
        return new Ticket(team, null, TicketType.BUG, TicketState.NEW, "Title", "Body",
                new User("u@example.com", "h"));
    }

    // AC-1: creating a ticket appends a single "created" entry stamped with the actor + clock instant.
    @Test
    void createAppendsCreatedActivity() {
        UUID teamId = UUID.randomUUID();
        Team team = new Team("Payments");
        User creator = new User("u@example.com", "hash");
        when(teams.findById(teamId)).thenReturn(Optional.of(team));
        when(users.findByEmail("u@example.com")).thenReturn(Optional.of(creator));
        when(tickets.save(any(Ticket.class))).thenAnswer(i -> i.getArgument(0));

        service().create(teamId, null, TicketType.BUG, TicketState.NEW, "T", "B", "u@example.com");

        ArgumentCaptor<TicketActivity> captor = ArgumentCaptor.forClass(TicketActivity.class);
        verify(activity).save(captor.capture());
        TicketActivity row = captor.getValue();
        assertThat(row.getField()).isEqualTo(TicketFieldChange.CREATED);
        assertThat(row.getActorEmail()).isEqualTo(creator.getEmail());
        assertThat(row.getOldValue()).isNull();
        assertThat(row.getNewValue()).isNull();
        assertThat(row.getOccurredAt()).isEqualTo(NOW);
    }

    // AC-2 (boundary): a multi-field edit logs one row per changed field; unchanged fields excluded.
    @Test
    void editLogsOneRowPerChangedFieldOnly() {
        UUID id = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Team team = new Team("Payments");
        Ticket ticket = existing(team); // BUG / NEW / "Title" / "Body"
        when(tickets.findById(id)).thenReturn(Optional.of(ticket));
        when(teams.findById(teamId)).thenReturn(Optional.of(team));

        // type BUG->FEATURE, state NEW->IN_PROGRESS, title changes; body unchanged, team/epic unchanged.
        service().update(id, teamId, null, TicketType.FEATURE, TicketState.IN_PROGRESS,
                "New title", "Body", "actor@example.com");

        ArgumentCaptor<TicketActivity> captor = ArgumentCaptor.forClass(TicketActivity.class);
        verify(activity, times(3)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(TicketActivity::getField, TicketActivity::getOldValue, TicketActivity::getNewValue)
                .containsExactly(
                        tuple(TicketFieldChange.TYPE, "bug", "feature"),
                        tuple(TicketFieldChange.STATE, "new", "in_progress"),
                        tuple(TicketFieldChange.TITLE, "Title", "New title"));
        assertThat(captor.getAllValues())
                .allSatisfy(row -> {
                    assertThat(row.getActorEmail()).isEqualTo("actor@example.com");
                    assertThat(row.getOccurredAt()).isEqualTo(NOW);
                });
    }

    // AC-3 (negative): an identical edit changes nothing, so no activity is recorded.
    @Test
    void noOpEditLogsNothing() {
        UUID id = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Team team = new Team("Payments");
        Ticket ticket = existing(team);
        when(tickets.findById(id)).thenReturn(Optional.of(ticket));
        when(teams.findById(teamId)).thenReturn(Optional.of(team));

        service().update(id, teamId, null, TicketType.BUG, TicketState.NEW, "Title", "Body",
                "actor@example.com");

        verify(activity, never()).save(any());
    }

    // AC-1: a real state transition logs a single "state" row with the old→new wire values.
    @Test
    void stateChangeLogsStateActivity() {
        UUID id = UUID.randomUUID();
        Ticket ticket = existing(new Team("Payments")); // NEW
        when(tickets.findById(id)).thenReturn(Optional.of(ticket));

        service().changeState(id, TicketState.DONE, "actor@example.com");

        ArgumentCaptor<TicketActivity> captor = ArgumentCaptor.forClass(TicketActivity.class);
        verify(activity).save(captor.capture());
        TicketActivity row = captor.getValue();
        assertThat(row.getField()).isEqualTo(TicketFieldChange.STATE);
        assertThat(row.getOldValue()).isEqualTo("new");
        assertThat(row.getNewValue()).isEqualTo("done");
        assertThat(row.getActorEmail()).isEqualTo("actor@example.com");
    }

    // AC-3 (boundary): re-setting the current state still bumps modified_at (HTS-027) but is not a
    // history-worthy transition, so no activity is recorded.
    @Test
    void resettingSameStateLogsNothing() {
        UUID id = UUID.randomUUID();
        Ticket ticket = existing(new Team("Payments")); // NEW
        when(tickets.findById(id)).thenReturn(Optional.of(ticket));

        service().changeState(id, TicketState.NEW, "actor@example.com");

        verify(activity, never()).save(any());
    }

    // AC-2: activity is read back oldest-first via the ordered repository query.
    @Test
    void activityReadDelegatesToOrderedQuery() {
        UUID id = UUID.randomUUID();
        Ticket ticket = existing(new Team("Payments"));
        when(tickets.findById(id)).thenReturn(Optional.of(ticket));
        when(activity.findByTicket_IdOrderBySeqAsc(id)).thenReturn(List.of());

        service().activity(id);

        verify(activity).findByTicket_IdOrderBySeqAsc(id);
    }
}
