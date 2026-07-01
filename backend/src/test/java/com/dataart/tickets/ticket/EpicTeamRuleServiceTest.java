package com.dataart.tickets.ticket;

import com.dataart.tickets.auth.User;
import com.dataart.tickets.auth.UserRepository;
import com.dataart.tickets.epic.Epic;
import com.dataart.tickets.epic.EpicRepository;
import com.dataart.tickets.team.Team;
import com.dataart.tickets.team.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the epic-same-team rule (HTS-021, FR-E7/FR-K5). Team/epic identities are mocked
 * so the rule can be exercised with distinct ids without a database.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EpicTeamRuleServiceTest {

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
        return new TicketService(tickets, teams, epics, users, activity,
                Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC));
    }

    private static Team teamWithId(UUID id) {
        Team team = mock(Team.class);
        lenient().when(team.getId()).thenReturn(id);
        return team;
    }

    private static Epic epicInTeam(UUID epicId, UUID teamId) {
        Epic epic = mock(Epic.class);
        lenient().when(epic.getId()).thenReturn(epicId);
        lenient().when(epic.getTeamId()).thenReturn(teamId);
        return epic;
    }

    private static User someUser() {
        return new User("u@example.com", "h");
    }

    // AC-1: same-team epic on create is accepted.
    @Test
    void createWithSameTeamEpicSucceeds() {
        UUID teamId = UUID.randomUUID();
        UUID epicId = UUID.randomUUID();
        Team team = teamWithId(teamId);
        Epic epic = epicInTeam(epicId, teamId);
        when(teams.findById(teamId)).thenReturn(Optional.of(team));
        when(epics.findById(epicId)).thenReturn(Optional.of(epic));
        when(users.findByEmail("u@example.com")).thenReturn(Optional.of(someUser()));
        when(tickets.save(any(Ticket.class))).thenAnswer(i -> i.getArgument(0));

        assertThatCode(() -> service().create(teamId, epicId, TicketType.BUG, TicketState.NEW,
                "T", "B", "u@example.com")).doesNotThrowAnyException();
    }

    // AC-2: cross-team epic on create → EPIC_TEAM_MISMATCH; nothing persisted.
    @Test
    void createWithCrossTeamEpicIsRejected() {
        UUID teamId = UUID.randomUUID();
        UUID otherTeamId = UUID.randomUUID();
        UUID epicId = UUID.randomUUID();
        Team team = teamWithId(teamId);
        Epic epic = epicInTeam(epicId, otherTeamId);
        when(teams.findById(teamId)).thenReturn(Optional.of(team));
        when(epics.findById(epicId)).thenReturn(Optional.of(epic));

        assertThatThrownBy(() -> service().create(teamId, epicId, TicketType.BUG, TicketState.NEW,
                "T", "B", "u@example.com"))
                .isInstanceOf(EpicTeamMismatchException.class);
    }

    // AC-4: null epic is always accepted.
    @Test
    void createWithNullEpicSucceeds() {
        UUID teamId = UUID.randomUUID();
        Team team = teamWithId(teamId);
        when(teams.findById(teamId)).thenReturn(Optional.of(team));
        when(users.findByEmail("u@example.com")).thenReturn(Optional.of(someUser()));
        when(tickets.save(any(Ticket.class))).thenAnswer(i -> i.getArgument(0));

        assertThatCode(() -> service().create(teamId, null, TicketType.BUG, TicketState.NEW,
                "T", "B", "u@example.com")).doesNotThrowAnyException();
    }

    // AC-3: changing team while keeping a now-cross-team epic is rejected.
    @Test
    void updateChangingTeamButKeepingCrossTeamEpicIsRejected() {
        UUID ticketId = UUID.randomUUID();
        UUID teamA = UUID.randomUUID();
        UUID teamB = UUID.randomUUID();
        UUID epicInA = UUID.randomUUID();
        Ticket existing = new Ticket(teamWithId(teamA), null, TicketType.BUG, TicketState.NEW,
                "T", "B", someUser());
        Team newTeam = teamWithId(teamB);
        Epic epic = epicInTeam(epicInA, teamA);
        when(tickets.findById(ticketId)).thenReturn(Optional.of(existing));
        when(teams.findById(teamB)).thenReturn(Optional.of(newTeam));
        when(epics.findById(epicInA)).thenReturn(Optional.of(epic));

        assertThatThrownBy(() -> service().update(ticketId, teamB, epicInA, TicketType.BUG,
                TicketState.NEW, "T", "B", "u@example.com"))
                .isInstanceOf(EpicTeamMismatchException.class);
    }

    // AC-3 (boundary): the same team change succeeds when the epic is cleared in the same request.
    @Test
    void updateChangingTeamAndClearingEpicSucceeds() {
        UUID ticketId = UUID.randomUUID();
        UUID teamA = UUID.randomUUID();
        UUID teamB = UUID.randomUUID();
        Ticket existing = new Ticket(teamWithId(teamA), null, TicketType.BUG, TicketState.NEW,
                "T", "B", someUser());
        Team newTeam = teamWithId(teamB);
        when(tickets.findById(ticketId)).thenReturn(Optional.of(existing));
        when(teams.findById(teamB)).thenReturn(Optional.of(newTeam));

        assertThatCode(() -> service().update(ticketId, teamB, null, TicketType.BUG,
                TicketState.NEW, "T", "B", "u@example.com")).doesNotThrowAnyException();
    }

    // AC-3 (boundary): the team change succeeds when the epic is replaced by a same-team epic.
    @Test
    void updateChangingTeamAndReplacingEpicSucceeds() {
        UUID ticketId = UUID.randomUUID();
        UUID teamA = UUID.randomUUID();
        UUID teamB = UUID.randomUUID();
        UUID epicInB = UUID.randomUUID();
        Ticket existing = new Ticket(teamWithId(teamA), null, TicketType.BUG, TicketState.NEW,
                "T", "B", someUser());
        Team newTeam = teamWithId(teamB);
        Epic epic = epicInTeam(epicInB, teamB);
        when(tickets.findById(ticketId)).thenReturn(Optional.of(existing));
        when(teams.findById(teamB)).thenReturn(Optional.of(newTeam));
        when(epics.findById(epicInB)).thenReturn(Optional.of(epic));

        assertThatCode(() -> service().update(ticketId, teamB, epicInB, TicketType.BUG,
                TicketState.NEW, "T", "B", "u@example.com")).doesNotThrowAnyException();
    }
}
