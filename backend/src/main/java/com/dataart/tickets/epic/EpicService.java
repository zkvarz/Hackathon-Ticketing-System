package com.dataart.tickets.epic;

import com.dataart.tickets.common.NotFoundException;
import com.dataart.tickets.team.Team;
import com.dataart.tickets.team.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Epic business logic (HTS-017, FR-E1..E5,E8). Team is fixed at creation (FR-E2); title is
 * trimmed/non-empty; deletion is blocked while tickets reference the epic (FR-E8).
 */
@Service
public class EpicService {

    private final EpicRepository epics;
    private final TeamRepository teams;
    private final List<EpicReferenceCounter> referenceCounters;

    public EpicService(EpicRepository epics, TeamRepository teams,
                       List<EpicReferenceCounter> referenceCounters) {
        this.epics = epics;
        this.teams = teams;
        this.referenceCounters = referenceCounters;
    }

    @Transactional(readOnly = true)
    public List<Epic> listByTeam(UUID teamId) {
        return epics.findByTeam_IdOrderByTitleAsc(teamId);
    }

    @Transactional(readOnly = true)
    public Epic get(UUID id) {
        return epics.findById(id).orElseThrow(() -> new NotFoundException("Epic not found: " + id));
    }

    @Transactional
    public Epic create(UUID teamId, String title, String description) {
        Team team = teams.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found: " + teamId));
        return epics.save(new Epic(team, title.trim(), normalizeDescription(description)));
    }

    /**
     * Update title/description. If {@code requestedTeamId} is supplied and differs from the
     * epic's team, the change is rejected (FR-E2).
     */
    @Transactional
    public Epic update(UUID id, String title, String description, UUID requestedTeamId) {
        Epic epic = get(id);
        if (requestedTeamId != null && !requestedTeamId.equals(epic.getTeamId())) {
            throw new EpicTeamImmutableException();
        }
        epic.setTitle(title.trim());
        epic.setDescription(normalizeDescription(description));
        return epic;
    }

    @Transactional
    public void delete(UUID id) {
        Epic epic = get(id);
        long refs = referenceCounters.stream().mapToLong(c -> c.countByEpic(id)).sum();
        if (refs > 0) {
            throw new EpicHasTicketsException();
        }
        epics.delete(epic);
    }

    @Transactional(readOnly = true)
    public long referenceCount(String label, UUID epicId) {
        return referenceCounters.stream()
                .filter(c -> c.label().equals(label))
                .mapToLong(c -> c.countByEpic(epicId))
                .findFirst()
                .orElse(0L);
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}
