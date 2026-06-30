package com.dataart.tickets.team;

import com.dataart.tickets.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for team business logic (HTS-015). The delete-with-children rule is exercised with
 * a mocked {@link TeamReferenceCounter} since the real counters (epics/tickets) arrive later.
 */
@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    private TeamRepository teams;

    // Positive: create trims the name and persists when unique.
    @Test
    void createTrimsAndSavesUniqueName() {
        TeamService service = new TeamService(teams, List.of());
        when(teams.existsByNameIgnoreCase("Payments")).thenReturn(false);
        when(teams.save(any(Team.class))).thenAnswer(i -> i.getArgument(0));

        Team result = service.create("  Payments  ");

        assertThat(result.getName()).isEqualTo("Payments");
    }

    // Negative: a case-insensitive duplicate is rejected without saving.
    @Test
    void createRejectsDuplicateName() {
        TeamService service = new TeamService(teams, List.of());
        when(teams.existsByNameIgnoreCase("payments")).thenReturn(true);

        assertThatThrownBy(() -> service.create("payments"))
                .isInstanceOf(TeamNameTakenException.class);
        verify(teams, never()).save(any());
    }

    // Boundary: rename excludes self from the uniqueness check and updates the name.
    @Test
    void renameUpdatesNameCheckingOtherTeams() {
        TeamService service = new TeamService(teams, List.of());
        UUID id = UUID.randomUUID();
        Team team = new Team("Old");
        when(teams.findById(id)).thenReturn(Optional.of(team));
        when(teams.existsByNameIgnoreCaseAndIdNot("New", id)).thenReturn(false);

        Team result = service.rename(id, "  New  ");

        assertThat(result.getName()).isEqualTo("New");
    }

    // Negative: renaming to an unknown id → 404.
    @Test
    void renameMissingTeamThrowsNotFound() {
        TeamService service = new TeamService(teams, List.of());
        UUID id = UUID.randomUUID();
        when(teams.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rename(id, "New"))
                .isInstanceOf(NotFoundException.class);
    }

    // Positive: deleting an unreferenced team (no counters) succeeds.
    @Test
    void deleteUnreferencedTeamSucceeds() {
        TeamService service = new TeamService(teams, List.of());
        UUID id = UUID.randomUUID();
        Team team = new Team("Empty");
        when(teams.findById(id)).thenReturn(Optional.of(team));

        service.delete(id);

        verify(teams).delete(team);
    }

    // Negative: a registered counter reporting children blocks deletion with 409.
    @Test
    void deleteBlockedWhenReferenced() {
        TeamReferenceCounter epics = new TeamReferenceCounter() {
            @Override
            public String label() {
                return "epics";
            }

            @Override
            public long countByTeam(UUID teamId) {
                return 2;
            }
        };
        TeamService service = new TeamService(teams, List.of(epics));
        UUID id = UUID.randomUUID();
        when(teams.findById(id)).thenReturn(Optional.of(new Team("Busy")));

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(TeamHasChildrenException.class);
        verify(teams, never()).delete(any());
    }
}
