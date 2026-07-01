package com.dataart.tickets.comment;

import com.dataart.tickets.auth.EmailNormalizer;
import com.dataart.tickets.auth.User;
import com.dataart.tickets.auth.UserRepository;
import com.dataart.tickets.common.NotFoundException;
import com.dataart.tickets.ticket.Ticket;
import com.dataart.tickets.ticket.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Comment business logic (HTS-023, FR-C1..C6; HTS-039 stretch). Adds non-empty comments authored by
 * the current principal and lists them oldest-first. Adding a comment loads the ticket only to
 * validate its existence and set the association — it never mutates the ticket, so the ticket's
 * {@code modified_at} is not advanced (FR-C5). The EP-09 stretch relaxes FR-C6: an author may edit
 * or delete their own comment (edit stamps {@code edited_at} from the clock; neither touches the
 * ticket).
 */
@Service
public class CommentService {

    private final CommentRepository comments;
    private final TicketRepository tickets;
    private final UserRepository users;
    private final Clock clock;

    public CommentService(CommentRepository comments, TicketRepository tickets, UserRepository users,
                          Clock clock) {
        this.comments = comments;
        this.tickets = tickets;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Comment> list(UUID ticketId) {
        requireTicket(ticketId);
        return comments.findByTicket_IdOrderByCreatedAtAscIdAsc(ticketId);
    }

    @Transactional
    public Comment add(UUID ticketId, String body, String authorEmail) {
        Ticket ticket = requireTicket(ticketId);
        User author = users.findByEmail(EmailNormalizer.normalize(authorEmail))
                .orElseThrow(() -> new NotFoundException("User not found: " + authorEmail));
        return comments.save(new Comment(ticket, author, body.trim()));
    }

    /**
     * Edit an existing comment (HTS-039). Only the author may edit; others → 403. The body is
     * trimmed and {@code edited_at} is stamped from the clock. The ticket is not mutated, so its
     * {@code modified_at} is untouched (FR-C5).
     *
     * @throws NotFoundException             if the comment does not exist under the given ticket
     * @throws CommentAccessDeniedException  if the requester is not the author
     */
    @Transactional
    public Comment update(UUID ticketId, UUID commentId, String body, String requesterEmail) {
        Comment comment = requireOwnComment(ticketId, commentId, requesterEmail);
        comment.edit(body.trim(), clock.instant());
        return comment;
    }

    /**
     * Delete an existing comment (HTS-039). Only the author may delete; others → 403.
     *
     * @throws NotFoundException             if the comment does not exist under the given ticket
     * @throws CommentAccessDeniedException  if the requester is not the author
     */
    @Transactional
    public void delete(UUID ticketId, UUID commentId, String requesterEmail) {
        Comment comment = requireOwnComment(ticketId, commentId, requesterEmail);
        comments.delete(comment);
    }

    /**
     * Load a comment, verify it belongs to the ticket in the path (else 404 — the nested resource
     * stays honest), and verify the requester authored it (else 403).
     */
    private Comment requireOwnComment(UUID ticketId, UUID commentId, String requesterEmail) {
        Comment comment = comments.findById(commentId)
                .filter(c -> ticketId.equals(c.getTicketId()))
                .orElseThrow(() -> new NotFoundException("Comment not found: " + commentId));
        if (!comment.getAuthorEmail().equals(EmailNormalizer.normalize(requesterEmail))) {
            throw new CommentAccessDeniedException();
        }
        return comment;
    }

    private Ticket requireTicket(UUID ticketId) {
        return tickets.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));
    }
}
