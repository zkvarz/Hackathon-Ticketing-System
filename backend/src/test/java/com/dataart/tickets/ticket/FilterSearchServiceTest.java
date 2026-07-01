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
 * Unit tests for the filter/search service layer (HTS-029): filters pass through to the query, a
 * blank {@code q} is normalized to null (ignored, not match-nothing), and {@code q} is trimmed.
 */
@ExtendWith(MockitoExtension.class)
class FilterSearchServiceTest {

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
        return new TicketService(tickets, teams, epics, users, activity, Clock.systemUTC());
    }

    @Test
    void filtersPassThroughToQuery() {
        UUID teamId = UUID.randomUUID();
        UUID epicId = UUID.randomUUID();
        when(tickets.search(teamId, TicketType.BUG, epicId, "pay")).thenReturn(List.of());

        service().search(teamId, TicketType.BUG, epicId, "pay");

        verify(tickets).search(teamId, TicketType.BUG, epicId, "pay");
    }

    @Test
    void blankQueryIsTreatedAsAbsent() {
        UUID teamId = UUID.randomUUID();
        when(tickets.search(teamId, null, null, null)).thenReturn(List.of());

        service().search(teamId, null, null, "   ");

        verify(tickets).search(teamId, null, null, null);
    }

    @Test
    void queryIsTrimmed() {
        UUID teamId = UUID.randomUUID();
        when(tickets.search(teamId, null, null, "login")).thenReturn(List.of());

        service().search(teamId, null, null, "  login  ");

        verify(tickets).search(teamId, null, null, "login");
    }
}
