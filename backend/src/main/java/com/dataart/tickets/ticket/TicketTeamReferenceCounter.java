package com.dataart.tickets.ticket;

import com.dataart.tickets.team.TeamReferenceCounter;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Registers tickets as a team reference (HTS-019): a team with tickets cannot be deleted (FR-T5).
 * Complements {@code EpicTeamReferenceCounter} in fully activating the team delete-block.
 */
@Component
public class TicketTeamReferenceCounter implements TeamReferenceCounter {

    private final TicketRepository tickets;

    public TicketTeamReferenceCounter(TicketRepository tickets) {
        this.tickets = tickets;
    }

    @Override
    public String label() {
        return "tickets";
    }

    @Override
    public long countByTeam(UUID teamId) {
        return tickets.countByTeam_Id(teamId);
    }
}
