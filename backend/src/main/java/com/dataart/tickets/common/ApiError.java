package com.dataart.tickets.common;

import java.time.Instant;
import java.util.List;

/**
 * Standard API error payload (architecture.md §8, FR-P4). Produced for every 4xx/5xx by
 * {@link ApiExceptionHandler} (and by the security filter chain's 401/403 handlers). The
 * {@code code} is the stable, machine-readable contract the frontend branches on.
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
