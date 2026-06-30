package com.dataart.tickets.team;

import com.dataart.tickets.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Team business logic (HTS-015, FR-T1..T6). Names are trimmed and unique case-insensitively;
 * deletion is blocked while any {@link TeamReferenceCounter} reports children (FR-T5).
 */
@Service
public class TeamService {

    private final TeamRepository teams;
    private final List<TeamReferenceCounter> referenceCounters;

    public TeamService(TeamRepository teams, List<TeamReferenceCounter> referenceCounters) {
        this.teams = teams;
        this.referenceCounters = referenceCounters;
    }

    @Transactional(readOnly = true)
    public List<Team> list() {
        return teams.findAllOrdered();
    }

    @Transactional(readOnly = true)
    public Team get(UUID id) {
        return teams.findById(id).orElseThrow(() -> new NotFoundException("Team not found: " + id));
    }

    @Transactional
    public Team create(String rawName) {
        String name = rawName.trim();
        if (teams.existsByNameIgnoreCase(name)) {
            throw new TeamNameTakenException(name);
        }
        return teams.save(new Team(name));
    }

    @Transactional
    public Team rename(UUID id, String rawName) {
        String name = rawName.trim();
        Team team = get(id);
        if (teams.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new TeamNameTakenException(name);
        }
        team.setName(name);
        return team; // dirty-checked; modified_at advances via BaseEntity
    }

    @Transactional
    public void delete(UUID id) {
        Team team = get(id);
        if (totalReferences(id) > 0) {
            throw new TeamHasChildrenException();
        }
        teams.delete(team);
    }

    /** Count of a single reference type (e.g. "epics"); 0 if no such counter is registered. */
    @Transactional(readOnly = true)
    public long referenceCount(String label, UUID teamId) {
        return referenceCounters.stream()
                .filter(c -> c.label().equals(label))
                .mapToLong(c -> c.countByTeam(teamId))
                .findFirst()
                .orElse(0L);
    }

    private long totalReferences(UUID teamId) {
        return referenceCounters.stream().mapToLong(c -> c.countByTeam(teamId)).sum();
    }
}
