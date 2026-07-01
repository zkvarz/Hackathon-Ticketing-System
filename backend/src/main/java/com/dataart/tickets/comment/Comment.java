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
 * created timestamp. Comments are immutable (FR-C6) and do not extend {@code BaseEntity} because
 * they have no {@code modified_at} — a comment never changes and adding one must not touch the
 * ticket's {@code modified_at} (FR-C5). Deleted with their ticket via DB cascade (FR-K6).
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
}
