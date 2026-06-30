package com.dataart.tickets.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByToken(String token);

    /** A user's outstanding (not-yet-consumed) tokens — used to invalidate them on resend (FR-A11). */
    List<EmailVerificationToken> findByUserAndConsumedAtIsNull(User user);
}
