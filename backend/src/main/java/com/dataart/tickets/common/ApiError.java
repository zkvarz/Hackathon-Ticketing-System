package com.dataart.tickets.common;

import java.time.Instant;
import java.util.List;

/**
 * Standard API error payload (architecture.md §8). Placeholder for the baseline — the global
 * exception handler that produces it is built in HTS-031.
 *
 * @param timestamp  when the error occurred (UTC)
 * @param status     HTTP status code
 * @param error      HTTP reason phrase
 * @param code       stable machine-readable code (e.g. TEAM_HAS_CHILDREN)
 * @param message    human-readable message
 * @param fieldErrors per-field validation errors (may be empty)
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {
    }
}
