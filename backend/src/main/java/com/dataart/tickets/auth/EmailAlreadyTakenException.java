package com.dataart.tickets.auth;

/**
 * Thrown when a signup uses an email that already exists (case-insensitively). Mapped to
 * HTTP 409 with code EMAIL_TAKEN by the API exception handler (FR-P4, architecture.md §8).
 */
public class EmailAlreadyTakenException extends RuntimeException {
    public EmailAlreadyTakenException(String email) {
        super("Email already registered: " + email);
    }
}
