package com.dataart.tickets.comment.dto;

import com.dataart.tickets.comment.Comment;

import java.time.Instant;
import java.util.UUID;

/**
 * Comment view (FR-C2). The author is exposed as an id and an email for display (AMB-8);
 * created_at is UTC ISO-8601 (FR-P5).
 */
public record CommentResponse(
        UUID id,
        UUID ticketId,
        UUID authorId,
        String authorEmail,
        String body,
        Instant createdAt
) {
    public static CommentResponse from(Comment comment) {
        return new CommentResponse(comment.getId(), comment.getTicketId(), comment.getAuthorId(),
                comment.getAuthorEmail(), comment.getBody(), comment.getCreatedAt());
    }
}
