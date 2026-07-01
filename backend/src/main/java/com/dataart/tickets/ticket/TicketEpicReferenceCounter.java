package com.dataart.tickets.ticket;

import com.dataart.tickets.epic.EpicReferenceCounter;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Registers tickets as an epic reference (HTS-019): an epic cannot be deleted while tickets
 * reference it (FR-E8). This bean activates the epic delete-block scaffolded in HTS-017 and drives
 * the {@code ticketCount} shown by the epic API.
 */
@Component
public class TicketEpicReferenceCounter implements EpicReferenceCounter {

    private final TicketRepository tickets;

    public TicketEpicReferenceCounter(TicketRepository tickets) {
        this.tickets = tickets;
    }

    @Override
    public String label() {
        return "tickets";
    }

    @Override
    public long countByEpic(UUID epicId) {
        return tickets.countByEpic_Id(epicId);
    }
}
