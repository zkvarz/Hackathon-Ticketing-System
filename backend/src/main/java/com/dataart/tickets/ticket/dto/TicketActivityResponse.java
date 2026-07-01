package com.dataart.tickets.ticket.dto;

import com.dataart.tickets.ticket.TicketActivity;

import java.time.Instant;
import java.util.UUID;

/**
 * A single ticket activity entry (HTS-041). {@code field} is one of the canonical field names (or
 * {@code created}); {@code oldValue}/{@code newValue} are human-readable strings captured at change
 * time (null for the creation entry). {@code at} is UTC ISO-8601 (FR-P5).
 */
public record TicketActivityResponse(
        UUID id,
        UUID ticketId,
        String actorEmail,
        String field,
        String oldValue,
        String newValue,
        Instant at
) {
    public static TicketActivityResponse from(TicketActivity activity) {
        return new TicketActivityResponse(activity.getId(), activity.getTicketId(),
                activity.getActorEmail(), activity.getField(), activity.getOldValue(),
                activity.getNewValue(), activity.getOccurredAt());
    }
}
