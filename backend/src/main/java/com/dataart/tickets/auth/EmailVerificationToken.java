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
 * Single-use email verification token (architecture.md §6, §10). 24h TTL (FR-A8); consumed
 * exactly once on successful verification. Issuing a new token invalidates prior unused ones
 * (FR-A11, wired in HTS-009).
 */
@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected EmailVerificationToken() {
        // JPA
    }

    public EmailVerificationToken(User user, String token, Instant expiresAt) {
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
