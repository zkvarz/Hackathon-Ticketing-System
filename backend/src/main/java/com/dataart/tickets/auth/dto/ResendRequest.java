package com.dataart.tickets.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Resend-verification request. Only an email is needed; the response is always generic so the
 * endpoint reveals nothing about whether the account exists (FR-A10, no enumeration).
 */
public record ResendRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "Must be a valid email address.")
        String email
) {
    public ResendRequest {
        if (email != null) {
            email = email.trim();
        }
    }
}
