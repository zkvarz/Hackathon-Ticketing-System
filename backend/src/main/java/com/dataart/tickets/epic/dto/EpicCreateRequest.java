package com.dataart.tickets.epic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create-epic body. Title trimmed + non-empty ≤200 (FR-E5 / AMB-1); description optional ≤10000.
 * The team comes from the {@code teamId} query parameter, not the body (FR-E2).
 */
public record EpicCreateRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must be at most 200 characters.")
        String title,

        @Size(max = 10000, message = "Description must be at most 10000 characters.")
        String description
) {
    public EpicCreateRequest {
        if (title != null) {
            title = title.trim();
        }
    }
}
