package com.dataart.tickets.auth;

/**
 * Thrown when a token (email verification or password reset) is unknown, already consumed, or
 * expired. Mapped to HTTP 400 with code TOKEN_INVALID (architecture.md §8). A single code is used
 * for all three cases so the response does not leak which tokens exist. The message varies by flow
 * (verification vs. reset) but the machine-readable code the FE branches on stays TOKEN_INVALID.
 */
public class TokenInvalidException extends RuntimeException {
    public TokenInvalidException() {
        this("The verification link is invalid or has expired.");
    }

    public TokenInvalidException(String message) {
        super(message);
    }
}
