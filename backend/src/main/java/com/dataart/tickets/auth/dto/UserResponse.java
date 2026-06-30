package com.dataart.tickets.auth.dto;

import com.dataart.tickets.auth.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Public view of a user. Deliberately omits the password hash (AC-1) — nothing about the
 * credential is ever returned.
 */
public record UserResponse(
        UUID id,
        String email,
        boolean emailVerified,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.isEmailVerified(),
                user.getCreatedAt());
    }
}
