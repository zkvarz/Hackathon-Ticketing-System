package com.dataart.tickets.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Reset-password request (HTS-037): the emailed single-use token plus the new password. Password
 * bounds are 8..128 (FR-A4 / AMB-1), re-checked server-side regardless of client validation. An
 * invalid/expired token is reported as TOKEN_INVALID by the service, not here.
 */
public record ResetPasswordRequest(
        @NotBlank(message = "Token is required.")
        String token,

        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters.")
        String password
) {
    public ResetPasswordRequest {
        if (token != null) {
            token = token.trim();
        }
    }
}
