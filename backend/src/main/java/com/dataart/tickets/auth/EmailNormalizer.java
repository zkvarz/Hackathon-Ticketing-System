package com.dataart.tickets.auth;

import java.util.Locale;

/**
 * Single source of truth for email normalization (FR-A2 / AMB-9): trim surrounding whitespace
 * and lower-case using the root locale (locale-independent, avoids the Turkish-i pitfall).
 * Storage and all lookups go through this so case/whitespace variants collide consistently.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
