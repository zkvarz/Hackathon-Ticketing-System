package com.dataart.tickets.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sign-up request body. Password bounds are 8..128 (FR-A4 / AMB-1); email must be well-formed
 * (FR-A1). The server is authoritative — these constraints are re-checked here regardless of
 * any client-side validation (FR-K8 spirit).
 */
public record SignupRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "Must be a valid email address.")
        @Size(max = 320, message = "Email must be at most 320 characters.")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters.")
        String password
) {
    public SignupRequest {
        // Trim surrounding whitespace on the email before validation so values like
        // "  User@Example.com " are accepted and then normalized (FR-A2). Passwords are left
        // untouched — leading/trailing spaces can be legitimate characters.
        if (email != null) {
            email = email.trim();
        }
    }
}

