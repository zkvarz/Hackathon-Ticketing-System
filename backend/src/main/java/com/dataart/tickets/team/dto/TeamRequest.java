package com.dataart.tickets.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create/rename team request. Name is trimmed then validated non-empty (FR-T4) with a defensive
 * 200-char cap (consistent with the AMB-1 title bound).
 */
public record TeamRequest(
        @NotBlank(message = "Team name is required.")
        @Size(max = 200, message = "Team name must be at most 200 characters.")
        String name
) {
    public TeamRequest {
        if (name != null) {
            name = name.trim();
        }
    }
}
