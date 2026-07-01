package com.dataart.tickets.ticket;

/**
 * A single field's old→new transition captured while applying a ticket edit (HTS-041). The values
 * are human-readable strings (team/epic display names, enum wire values, or the raw title/body) so
 * the activity log can be rendered without re-resolving references. {@link TicketService} turns each
 * one into an append-only {@link TicketActivity} row.
 */
public record TicketFieldChange(String field, String oldValue, String newValue) {

    // Canonical field names used across the activity log (and matched by the frontend, HTS-042).
    public static final String TEAM = "team";
    public static final String EPIC = "epic";
    public static final String TYPE = "type";
    public static final String STATE = "state";
    public static final String TITLE = "title";
    public static final String BODY = "body";
    /** Marks the creation entry (no old/new value). */
    public static final String CREATED = "created";
}
