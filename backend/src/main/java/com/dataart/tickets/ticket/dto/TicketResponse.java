package com.dataart.tickets.ticket.dto;

import com.dataart.tickets.ticket.Ticket;
import com.dataart.tickets.ticket.TicketState;
import com.dataart.tickets.ticket.TicketType;

import java.time.Instant;
import java.util.UUID;

/**
 * Ticket view returning all fields, including the creator identity and created/modified metadata
 * (FR-K2). The creator is exposed both as an id and as an email for display (AMB-8); the epic is
 * exposed as id + title so the board card can show it (FR-B; HTS-025). Timestamps are UTC
 * ISO-8601 (FR-P5).
 */
public record TicketResponse(
        UUID id,
        UUID teamId,
        UUID epicId,
        String epicTitle,
        TicketType type,
        TicketState state,
        String title,
        String body,
        UUID createdBy,
        String createdByEmail,
        Instant createdAt,
        Instant modifiedAt
) {
    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getTeamId(),
                ticket.getEpicId(),
                ticket.getEpicTitle(),
                ticket.getType(),
                ticket.getState(),
                ticket.getTitle(),
                ticket.getBody(),
                ticket.getCreatedById(),
                ticket.getCreatedByEmail(),
                ticket.getCreatedAt(),
                ticket.getModifiedAt());
    }
}
