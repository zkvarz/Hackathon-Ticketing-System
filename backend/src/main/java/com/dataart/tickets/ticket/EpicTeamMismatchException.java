package com.dataart.tickets.ticket;

/**
 * A ticket's epic must belong to the same team as the ticket (FR-E7). Raised on create/update
 * when a non-null epic references a different team — including when a team change would leave the
 * currently-selected epic cross-team (FR-K5). Mapped to HTTP 400 with code EPIC_TEAM_MISMATCH.
 */
public class EpicTeamMismatchException extends RuntimeException {
    public EpicTeamMismatchException() {
        super("The selected epic belongs to a different team than the ticket.");
    }
}
