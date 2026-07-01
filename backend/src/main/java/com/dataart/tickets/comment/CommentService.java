package com.dataart.tickets.comment;

import com.dataart.tickets.auth.EmailNormalizer;
import com.dataart.tickets.auth.User;
import com.dataart.tickets.auth.UserRepository;
import com.dataart.tickets.common.NotFoundException;
import com.dataart.tickets.ticket.Ticket;
import com.dataart.tickets.ticket.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Comment business logic (HTS-023, FR-C1..C6). Adds non-empty comments authored by the current
 * principal and lists them oldest-first. Adding a comment loads the ticket only to validate its
 * existence and set the association — it never mutates the ticket, so the ticket's
 * {@code modified_at} is not advanced (FR-C5). Comments are immutable: no edit/delete here (FR-C6).
 */
@Service
public class CommentService {

    private final CommentRepository comments;
    private final TicketRepository tickets;
    private final UserRepository users;

    public CommentService(CommentRepository comments, TicketRepository tickets, UserRepository users) {
        this.comments = comments;
        this.tickets = tickets;
        this.users = users;
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

    private Ticket requireTicket(UUID ticketId) {
        return tickets.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));
    }
}
