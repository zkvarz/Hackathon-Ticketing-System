package com.dataart.tickets.epic;

/**
 * An epic cannot be deleted while tickets reference it (FR-E8). Mapped to HTTP 409 with code
 * EPIC_HAS_TICKETS.
 */
public class EpicHasTicketsException extends RuntimeException {
    public EpicHasTicketsException() {
        super("Epic has tickets and cannot be deleted.");
    }
}
