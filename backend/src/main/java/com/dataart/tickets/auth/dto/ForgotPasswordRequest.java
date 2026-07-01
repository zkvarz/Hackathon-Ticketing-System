package com.dataart.tickets.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Forgot-password request (HTS-037). Only an email is needed; the response is always generic so the
 * endpoint reveals nothing about whether the account exists (no enumeration).
 */
public record ForgotPasswordRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "Must be a valid email address.")
        String email
) {
    public ForgotPasswordRequest {
        if (email != null) {
            email = email.trim();
        }
    }
}
