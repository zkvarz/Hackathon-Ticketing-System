package com.dataart.tickets.common;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * Generates time-ordered UUIDv7 values (RFC 9562). Chosen for entity IDs (analysis A-5):
 * non-enumerable in URLs while keeping good index locality because the leading 48 bits are
 * a millisecond timestamp.
 *
 * Layout: [48-bit unix-millis timestamp][4-bit version=7][12-bit rand_a]
 *         [2-bit variant=0b10][62-bit rand_b].
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    /** Generate a new time-ordered UUIDv7. */
    public static UUID generate() {
        long timestamp = Instant.now().toEpochMilli();

        byte[] value = new byte[16];
        // 48-bit big-endian millisecond timestamp.
        value[0] = (byte) (timestamp >>> 40);
        value[1] = (byte) (timestamp >>> 32);
        value[2] = (byte) (timestamp >>> 24);
        value[3] = (byte) (timestamp >>> 16);
        value[4] = (byte) (timestamp >>> 8);
        value[5] = (byte) timestamp;

        // Remaining 10 bytes are random; version/variant bits are overwritten below.
        byte[] rand = new byte[10];
        RANDOM.nextBytes(rand);
        System.arraycopy(rand, 0, value, 6, 10);

        // Version 7 in the high nibble of byte 6.
        value[6] = (byte) ((value[6] & 0x0F) | 0x70);
        // RFC 4122/9562 variant (0b10) in the top bits of byte 8.
        value[8] = (byte) ((value[8] & 0x3F) | 0x80);

        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (value[i] & 0xFF);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (value[i] & 0xFF);
        }
        return new UUID(msb, lsb);
    }

    /** Extract the embedded 48-bit millisecond timestamp from a UUIDv7. */
    public static long extractTimestamp(UUID uuid) {
        return uuid.getMostSignificantBits() >>> 16;
    }
}
