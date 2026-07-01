package com.dataart.tickets.ticket.dto;

import com.dataart.tickets.ticket.TicketState;
import jakarta.validation.constraints.NotNull;

/**
 * State-change body for {@code PATCH /api/tickets/{id}/state} (HTS-027, FR-K7). A missing state
 * fails validation (400); an unknown value fails enum deserialization (400) before validation.
 */
public record StateChangeRequest(
        @NotNull(message = "State is required.")
        TicketState state
) {
}
