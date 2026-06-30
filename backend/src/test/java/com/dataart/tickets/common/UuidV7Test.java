package com.dataart.tickets.common;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7Test {

    // Positive: a generated value is a well-formed version-7, IETF-variant UUID.
    @Test
    void generatesVersion7VariantUuid() {
        UUID uuid = UuidV7.generate();
        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2); // IETF (RFC 4122/9562) variant.
    }

    // Positive: embedded timestamp is close to "now".
    @Test
    void embedsCurrentTimestamp() {
        long before = System.currentTimeMillis();
        UUID uuid = UuidV7.generate();
        long after = System.currentTimeMillis();
        long ts = UuidV7.extractTimestamp(uuid);
        assertThat(ts).isBetween(before, after);
    }

    // Boundary/sanity: many values are unique...
    @Test
    void generatesUniqueValues() {
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertThat(seen.add(UuidV7.generate())).isTrue();
        }
    }

    // Boundary: IDs generated over time are time-ordered by their embedded timestamp
    // (non-decreasing), which is the property that gives UUIDv7 good index locality.
    @Test
    void timestampsAreNonDecreasing() throws InterruptedException {
        long previous = UuidV7.extractTimestamp(UuidV7.generate());
        for (int i = 0; i < 5; i++) {
            Thread.sleep(2);
            long current = UuidV7.extractTimestamp(UuidV7.generate());
            assertThat(current).isGreaterThanOrEqualTo(previous);
            previous = current;
        }
    }
}
