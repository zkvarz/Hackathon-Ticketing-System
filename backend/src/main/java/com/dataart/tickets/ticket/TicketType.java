package com.dataart.tickets.ticket;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Ticket type (architecture.md §6, FR-K8). API canonical (wire) values are lowercase
 * {@code bug | feature | fix}; the constant name is what JPA persists (@Enumerated STRING) and is
 * backed by a CHECK constraint in the migration. Deserialization is case-insensitive and rejects
 * unknown values with {@link IllegalArgumentException}, which surfaces as HTTP 400.
 */
public enum TicketType {
    BUG("bug"),
    FEATURE("feature"),
    FIX("fix");

    private final String wire;

    TicketType(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static TicketType fromWire(String value) {
        if (value != null) {
            String normalized = value.trim();
            for (TicketType type : values()) {
                if (type.wire.equalsIgnoreCase(normalized)) {
                    return type;
                }
            }
        }
        throw new IllegalArgumentException(
                "Invalid ticket type: " + value + " (expected one of bug, feature, fix)");
    }
}
