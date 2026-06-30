package com.dataart.tickets.epic;

import com.dataart.tickets.team.TeamReferenceCounter;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Registers epics as a team reference (HTS-017): a team with epics cannot be deleted (FR-T5).
 * This bean is what activates the team delete-block defined generically in HTS-015.
 */
@Component
public class EpicTeamReferenceCounter implements TeamReferenceCounter {

    private final EpicRepository epics;

    public EpicTeamReferenceCounter(EpicRepository epics) {
        this.epics = epics;
    }

    @Override
    public String label() {
        return "epics";
    }

    @Override
    public long countByTeam(UUID teamId) {
        return epics.countByTeam_Id(teamId);
    }
}
