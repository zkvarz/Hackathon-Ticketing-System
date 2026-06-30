package com.dataart.tickets.team;

/**
 * A team name collides case-insensitively with an existing one (FR-T4). Mapped to HTTP 409 with
 * code NAME_TAKEN.
 */
public class TeamNameTakenException extends RuntimeException {
    public TeamNameTakenException(String name) {
        super("A team named '" + name + "' already exists.");
    }
}
