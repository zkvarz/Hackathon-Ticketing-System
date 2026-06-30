package com.dataart.tickets.auth;

/**
 * Thrown when a verification token is unknown, already consumed, or expired. Mapped to HTTP 400
 * with code TOKEN_INVALID (architecture.md §8). A single code is used for all three cases so the
 * response does not leak which tokens exist.
 */
public class TokenInvalidException extends RuntimeException {
    public TokenInvalidException() {
        super("The verification link is invalid or has expired.");
    }
}
