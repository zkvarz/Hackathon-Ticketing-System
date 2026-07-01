package com.dataart.tickets.ticket.dto;

import com.dataart.tickets.ticket.TicketState;
import com.dataart.tickets.ticket.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Create/update-ticket body (architecture.md §6/§8, FR-K1/K3/K8; AMB-1). Title trimmed/non-empty
 * ≤200, body trimmed/non-empty ≤10000. {@code teamId} is required (the team is editable, FR-K3);
 * {@code epicId} is optional (FR-E6). {@code createdBy} is never accepted from the client — it is
 * taken from the authenticated principal server-side.
 *
 * <p>Invalid {@code type}/{@code state} values fail enum deserialization and surface as HTTP 400
 * before bean validation runs.
 */
public record TicketRequest(
        @NotNull(message = "Team is required.")
        UUID teamId,

        UUID epicId,

        @NotNull(message = "Type is required.")
        TicketType type,

        @NotNull(message = "State is required.")
        TicketState state,

        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must be at most 200 characters.")
        String title,

        @NotBlank(message = "Body is required.")
        @Size(max = 10000, message = "Body must be at most 10000 characters.")
        String body
) {
    public TicketRequest {
        if (title != null) {
            title = title.trim();
        }
        if (body != null) {
            body = body.trim();
        }
    }
}
