package com.dataart.tickets.common;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.Instant;
import java.util.UUID;

/**
 * Base for all persistent entities: a UUIDv7 primary key and server-set UTC timestamps
 * (analysis A-5, A-6). Feature entities (Team, Epic, Ticket, ...) will extend this in later
 * tickets; no table is mapped here.
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UuidV7.generate();
        }
        Instant now = Instant.now();
        createdAt = now;
        modifiedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        modifiedAt = Instant.now();
    }

    /**
     * Advance {@code modifiedAt} to an explicit instant. Used where a service must record a change
     * time deterministically from an injected clock (e.g. the ticket "modified only on real
     * change" rule, AMB-3/FR-K4) so the in-memory entity — and the response mapped from it — is
     * immediately consistent, independent of the later {@link #onUpdate()} flush callback.
     */
    protected void markModified(Instant when) {
        this.modifiedAt = when;
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getModifiedAt() {
        return modifiedAt;
    }
}
