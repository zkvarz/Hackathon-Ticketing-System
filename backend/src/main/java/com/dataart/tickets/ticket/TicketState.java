package com.dataart.tickets.ticket;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Ticket workflow state (architecture.md §6, FR-K8, FR-B1). The five canonical (wire) values in
 * workflow order are {@code new, ready_for_implementation, in_progress, ready_for_acceptance,
 * done}; the constant name is persisted (@Enumerated STRING) and CHECK-constrained in the
 * migration. Deserialization is case-insensitive and rejects unknown values (→ HTTP 400).
 */
public enum TicketState {
    NEW("new"),
    READY_FOR_IMPLEMENTATION("ready_for_implementation"),
    IN_PROGRESS("in_progress"),
    READY_FOR_ACCEPTANCE("ready_for_acceptance"),
    DONE("done");

    private final String wire;

    TicketState(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static TicketState fromWire(String value) {
        if (value != null) {
            String normalized = value.trim();
            for (TicketState state : values()) {
                if (state.wire.equalsIgnoreCase(normalized)) {
                    return state;
                }
            }
        }
        throw new IllegalArgumentException("Invalid ticket state: " + value + " (expected one of "
                + "new, ready_for_implementation, in_progress, ready_for_acceptance, done)");
    }
}
