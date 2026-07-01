package com.dataart.tickets.comment;

import com.dataart.tickets.auth.User;
import com.dataart.tickets.common.UuidV7;
import com.dataart.tickets.ticket.Ticket;
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
 * Comment (architecture.md §6). Belongs to a ticket and an author; carries a body and a server-set
 * created timestamp. Comments do not extend {@code BaseEntity} because they have no
 * {@code modified_at} — editing a comment must not touch the ticket's {@code modified_at} (FR-C5).
 * Originally immutable (FR-C6); the EP-09 stretch (HTS-039) relaxes that so an author may edit or
 * delete their own comment, recording {@code edited_at} on edit. Deleted with their ticket via DB
 * cascade (FR-K6).
 */
@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Null until the author first edits the comment (HTS-039). A non-null value marks it "edited".
    @Column(name = "edited_at")
    private Instant editedAt;

    protected Comment() {
        // JPA
    }

    public Comment(Ticket ticket, User author, String body) {
        this.ticket = ticket;
        this.author = author;
        this.body = body;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UuidV7.generate();
        }
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTicketId() {
        return ticket.getId();
    }

    public User getAuthor() {
        return author;
    }

    public UUID getAuthorId() {
        return author.getId();
    }

    public String getAuthorEmail() {
        return author.getEmail();
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getEditedAt() {
        return editedAt;
    }

    /**
     * Replace the body and stamp {@code edited_at} (HTS-039). Caller passes the trimmed body and the
     * clock instant. Only the comment is mutated — the ticket's {@code modified_at} is untouched
     * (FR-C5).
     */
    public void edit(String newBody, Instant editedAt) {
        this.body = newBody;
        this.editedAt = editedAt;
    }
}
