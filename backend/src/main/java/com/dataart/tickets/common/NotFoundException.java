package com.dataart.tickets.common;

/**
 * A requested resource does not exist. Mapped to HTTP 404 with code NOT_FOUND
 * (architecture.md §8, FR-P4).
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
