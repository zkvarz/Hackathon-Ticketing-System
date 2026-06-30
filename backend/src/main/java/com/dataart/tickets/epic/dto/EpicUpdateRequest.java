package com.dataart.tickets.epic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Update-epic body. {@code teamId} is optional and only used to detect (and reject) an attempt
 * to move the epic to a different team (FR-E2) — the team is never actually changed.
 */
public record EpicUpdateRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must be at most 200 characters.")
        String title,

        @Size(max = 10000, message = "Description must be at most 10000 characters.")
        String description,

        UUID teamId
) {
    public EpicUpdateRequest {
        if (title != null) {
            title = title.trim();
        }
    }
}
