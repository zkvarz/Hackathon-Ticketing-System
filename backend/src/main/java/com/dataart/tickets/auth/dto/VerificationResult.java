package com.dataart.tickets.auth.dto;

/**
 * Result of a successful email verification. Deliberately minimal — verification does not log
 * the user in (FR-A9); the SPA routes to the login screen on success.
 */
public record VerificationResult(String status, String email) {
}
