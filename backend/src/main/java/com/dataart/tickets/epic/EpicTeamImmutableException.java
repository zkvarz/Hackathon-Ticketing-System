package com.dataart.tickets.epic;

/**
 * Attempt to change an epic's team after creation (FR-E2). Mapped to HTTP 400 with code
 * EPIC_TEAM_IMMUTABLE.
 */
public class EpicTeamImmutableException extends RuntimeException {
    public EpicTeamImmutableException() {
        super("An epic's team cannot be changed after creation.");
    }
}
