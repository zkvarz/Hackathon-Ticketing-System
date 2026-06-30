package com.dataart.tickets.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Login request. Email is trimmed (then normalized server-side); password is taken as-is.
 * No format/length constraints beyond non-blank — authentication, not registration, decides
 * validity, and a generic 401 avoids leaking which field was wrong.
 */
public record LoginRequest(
        @NotBlank(message = "Email is required.") String email,
        @NotBlank(message = "Password is required.") String password
) {
    public LoginRequest {
        if (email != null) {
            email = email.trim();
        }
    }
}
