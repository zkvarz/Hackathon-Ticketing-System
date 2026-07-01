package com.dataart.tickets.ticket;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ticket enum parsing (FR-K8). Wire values are the canonical lowercase forms;
 * parsing is case-insensitive and rejects unknown values (which the API surfaces as HTTP 400).
 */
class TicketEnumTest {

    @Test
    void typeParsesCanonicalAndIsCaseInsensitive() {
        assertThat(TicketType.fromWire("bug")).isEqualTo(TicketType.BUG);
        assertThat(TicketType.fromWire("Feature")).isEqualTo(TicketType.FEATURE);
        assertThat(TicketType.fromWire(" FIX ")).isEqualTo(TicketType.FIX);
        assertThat(TicketType.BUG.wire()).isEqualTo("bug");
    }

    @Test
    void stateParsesCanonicalAndIsCaseInsensitive() {
        assertThat(TicketState.fromWire("new")).isEqualTo(TicketState.NEW);
        assertThat(TicketState.fromWire("ready_for_implementation"))
                .isEqualTo(TicketState.READY_FOR_IMPLEMENTATION);
        assertThat(TicketState.fromWire("IN_PROGRESS")).isEqualTo(TicketState.IN_PROGRESS);
        assertThat(TicketState.DONE.wire()).isEqualTo("done");
    }

    @Test
    void invalidTypeIsRejected() {
        assertThatThrownBy(() -> TicketType.fromWire("banana"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TicketType.fromWire(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidStateIsRejected() {
        assertThatThrownBy(() -> TicketState.fromWire("archived"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
