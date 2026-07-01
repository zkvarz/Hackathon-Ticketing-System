package com.dataart.tickets.comment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    // Chronological, oldest-first (FR-C4). id is a UUIDv7 (time-ordered) tie-break so the order is
    // stable when two comments share a created_at. The author is fetched for its display email.
    @EntityGraph(attributePaths = "author")
    List<Comment> findByTicket_IdOrderByCreatedAtAscIdAsc(UUID ticketId);
}
