package com.dataart.tickets.team;

/**
 * A team cannot be deleted while tickets or epics reference it (FR-T5). Mapped to HTTP 409 with
 * code TEAM_HAS_CHILDREN.
 */
public class TeamHasChildrenException extends RuntimeException {
    public TeamHasChildrenException() {
        super("Team has tickets or epics and cannot be deleted.");
    }
}
