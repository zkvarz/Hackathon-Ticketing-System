package com.dataart.tickets.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Base for all persistent entities: a UUIDv7 primary key and server-set UTC timestamps
 * (analysis A-5, A-6). Feature entities (Team, Epic, Ticket, ...) extend this; no table is mapped
 * here.
 *
 * <p>Timestamps are single-sourced by <strong>Spring Data JPA Auditing</strong> (HTS-047), driven
 * by the app {@link java.time.Clock} through a {@code DateTimeProvider} (see
 * {@code JpaAuditingConfig}). {@code @CreatedDate}/{@code @LastModifiedDate} are set from that one
 * clock on persist and on every real update, so the persisted instant equals the one the service
 * returns, and the value is deterministic under a fixed clock. Auditing's {@code @LastModifiedDate}
 * fires on the JPA update callback, which only runs when Hibernate detects a real field change — so
 * the AMB-3 "advance only on a real change" rule (FR-K4) needs no service-side timestamp write.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt;

    // Only assigns the id; timestamps are owned by JPA Auditing. UUIDv7 is generated here (not by
    // the DB) so the id is available in-memory immediately after construction/persist.
    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UuidV7.generate();
        }
    }

    /**
     * Force {@code modifiedAt} to an explicit instant. This is <em>not</em> the source of truth for
     * the timestamp — JPA Auditing re-stamps it from the same clock at flush. Its sole purpose is
     * the documented HTS-027 exception: a state PATCH must advance {@code modifiedAt} even when the
     * target state equals the current one, which would otherwise leave the row un-dirtied and skip
     * the auditing update. Writing here dirties the row so the auditing callback runs (FR-K7).
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
