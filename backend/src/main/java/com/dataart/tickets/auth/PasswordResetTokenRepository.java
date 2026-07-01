package com.dataart.tickets.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByToken(String token);

    /** A user's outstanding (not-yet-consumed) reset tokens — invalidated on reissue / consume. */
    List<PasswordResetToken> findByUserAndConsumedAtIsNull(User user);
}
