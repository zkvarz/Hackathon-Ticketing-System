package com.dataart.tickets.epic;

import com.dataart.tickets.common.NotFoundException;
import com.dataart.tickets.team.Team;
import com.dataart.tickets.team.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for epic business logic (HTS-017). Ticket-referenced delete uses a mocked
 * {@link EpicReferenceCounter} (real ticket counter arrives in HTS-019).
 */
@ExtendWith(MockitoExtension.class)
class EpicServiceTest {

    @Mock
    private EpicRepository epics;
    @Mock
    private TeamRepository teams;

    private EpicService service(List<EpicReferenceCounter> counters) {
        return new EpicService(epics, teams, counters);
    }

    // Positive: create validates the team exists, trims title, blanks empty description to null.
    @Test
    void createSavesUnderExistingTeam() {
        UUID teamId = UUID.randomUUID();
        when(teams.findById(teamId)).thenReturn(Optional.of(new Team("Payments")));
        when(epics.save(any(Epic.class))).thenAnswer(i -> i.getArgument(0));

        Epic epic = service(List.of()).create(teamId, "  Checkout  ", "   ");

        assertThat(epic.getTitle()).isEqualTo("Checkout");
        assertThat(epic.getDescription()).isNull();
    }

    // Negative: creating under a missing team → 404.
    @Test
    void createUnderMissingTeamThrowsNotFound() {
        UUID teamId = UUID.randomUUID();
        when(teams.findById(teamId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(List.of()).create(teamId, "X", null))
                .isInstanceOf(NotFoundException.class);
        verify(epics, never()).save(any());
    }

    // Negative: attempting to change the team on update → 400 EPIC_TEAM_IMMUTABLE.
    @Test
    void updateRejectsTeamChange() {
        UUID epicId = UUID.randomUUID();
        Team team = new Team("Payments");
        Epic epic = new Epic(team, "Old", null);
        when(epics.findById(epicId)).thenReturn(Optional.of(epic));

        assertThatThrownBy(() -> service(List.of()).update(epicId, "New", null, UUID.randomUUID()))
                .isInstanceOf(EpicTeamImmutableException.class);
    }

    // Positive: update with no team change (or matching team) updates fields.
    @Test
    void updateChangesTitleAndDescription() {
        UUID epicId = UUID.randomUUID();
        Epic epic = new Epic(new Team("Payments"), "Old", "old");
        when(epics.findById(epicId)).thenReturn(Optional.of(epic));

        Epic result = service(List.of()).update(epicId, "  New  ", "desc", null);

        assertThat(result.getTitle()).isEqualTo("New");
        assertThat(result.getDescription()).isEqualTo("desc");
    }

    // Negative: a registered counter reporting tickets blocks delete with 409.
    @Test
    void deleteBlockedWhenReferencedByTickets() {
        EpicReferenceCounter tickets = new EpicReferenceCounter() {
            @Override
            public String label() {
                return "tickets";
            }

            @Override
            public long countByEpic(UUID epicId) {
                return 3;
            }
        };
        UUID epicId = UUID.randomUUID();
        when(epics.findById(epicId)).thenReturn(Optional.of(new Epic(new Team("P"), "E", null)));

        assertThatThrownBy(() -> service(List.of(tickets)).delete(epicId))
                .isInstanceOf(EpicHasTicketsException.class);
        verify(epics, never()).delete(any());
    }
}
