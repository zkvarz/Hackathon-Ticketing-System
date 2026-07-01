package com.dataart.tickets.ticket;

import com.dataart.tickets.common.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only record of a single change to a ticket (HTS-041, architecture.md §6). One row is
 * written per changed field on create/edit and per state transition, within the same transaction as
 * the mutation. Rows are never updated or deleted (there is no edit/delete path — AC-4); they cascade
 * away with their ticket (FR-K6).
 *
 * <p>Like {@link com.dataart.tickets.comment.Comment}, this does not extend {@code BaseEntity}: it
 * is immutable, so it has no {@code modified_at}. The actor is the authenticated principal's email;
 * old/new values are captured as human-readable strings ({@link TicketFieldChange}) at the time of
 * the change, so the log stays accurate even if a referenced team/epic is later renamed or removed.
 */
@Entity
@Table(name = "ticket_activity")
public class TicketActivity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    // DB-assigned monotonic ordering key (see V12 migration). Never written by the app — the database
    // fills it in insert order; loaded on read purely to sort the history chronologically, since
    // rows from one multi-field edit share an occurred_at that a UUIDv7 id cannot tie-break.
    @Column(name = "seq", insertable = false, updatable = false)
    private Long seq;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
    private Ticket ticket;

    @Column(name = "actor_email", nullable = false, updatable = false)
    private String actorEmail;

    @Column(nullable = false, updatable = false, length = 30)
    private String field;

    @Column(name = "old_value", columnDefinition = "text", updatable = false)
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text", updatable = false)
    private String newValue;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected TicketActivity() {
        // JPA
    }

    public TicketActivity(Ticket ticket, String actorEmail, String field,
                          String oldValue, String newValue, Instant occurredAt) {
        this.ticket = ticket;
        this.actorEmail = actorEmail;
        this.field = field;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.occurredAt = occurredAt;
    }

    /** Build a row from a captured field diff (edit path). */
    public static TicketActivity of(Ticket ticket, String actorEmail, TicketFieldChange change,
                                    Instant occurredAt) {
        return new TicketActivity(ticket, actorEmail, change.field(),
                change.oldValue(), change.newValue(), occurredAt);
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UuidV7.generate();
        }
    }

    public UUID getId() {
        return id;
    }

    public Long getSeq() {
        return seq;
    }

    public UUID getTicketId() {
        return ticket.getId();
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public String getField() {
        return field;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
