package com.dataart.tickets.auth;

import com.dataart.tickets.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Single-use password reset token (HTS-037, architecture.md §6, §10). Short TTL (1h default);
 * consumed exactly once on a successful reset. Issuing a new token invalidates prior unused ones
 * for the same user. Structurally mirrors {@link EmailVerificationToken} but is a distinct entity
 * and table so the reset and verification flows never share a token space.
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected PasswordResetToken() {
        // JPA
    }

    public PasswordResetToken(User user, String token, Instant expiresAt) {
        this.user = user;
        this.token = token;
        this.expiresAt = expiresAt;
    }

    public User getUser() {
        return user;
    }

    public String getToken() {
        return token;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }

    /** True if the token has already been used. */
    public boolean isConsumed() {
        return consumedAt != null;
    }

    /** True if the token is expired at the given instant (expiry is exclusive). */
    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
