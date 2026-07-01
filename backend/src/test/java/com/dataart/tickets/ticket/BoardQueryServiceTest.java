package com.dataart.tickets.ticket;

import com.dataart.tickets.auth.UserRepository;
import com.dataart.tickets.epic.EpicRepository;
import com.dataart.tickets.team.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for the board query (HTS-025): the service delegates to the ordered, team-scoped
 * repository query (modified-desc + id tie-break).
 */
@ExtendWith(MockitoExtension.class)
class BoardQueryServiceTest {

    @Mock
    private TicketRepository tickets;
    @Mock
    private TeamRepository teams;
    @Mock
    private EpicRepository epics;
    @Mock
    private UserRepository users;

    @Test
    void listByTeamUsesOrderedTeamScopedQuery() {
        UUID teamId = UUID.randomUUID();
        when(tickets.findByTeam_IdOrderByModifiedAtDescIdDesc(teamId)).thenReturn(List.of());
        TicketService service = new TicketService(tickets, teams, epics, users, Clock.systemUTC());

        service.listByTeam(teamId);

        verify(tickets).findByTeam_IdOrderByModifiedAtDescIdDesc(teamId);
    }
}
