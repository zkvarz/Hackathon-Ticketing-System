package com.dataart.tickets.comment;

import com.dataart.tickets.auth.User;
import com.dataart.tickets.auth.UserRepository;
import com.dataart.tickets.common.NotFoundException;
import com.dataart.tickets.ticket.Ticket;
import com.dataart.tickets.ticket.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for comment business logic (HTS-023 + HTS-039). Covers author-from-principal, body
 * trimming, the 404s for a missing ticket/principal, and the stretch edit/delete authorization:
 * author edits stamp {@code edited_at}; non-authors get 403; missing/wrong-ticket → 404.
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    private static final Instant EDIT_TIME = Instant.parse("2026-06-30T12:00:00Z");

    @Mock
    private CommentRepository comments;
    @Mock
    private TicketRepository tickets;
    @Mock
    private UserRepository users;

    private CommentService service() {
        return new CommentService(comments, tickets, users, Clock.fixed(EDIT_TIME, ZoneOffset.UTC));
    }

    /** A saved comment authored by {@code authorEmail} on a ticket whose id is {@code ticketId}. */
    private Comment ownedComment(UUID ticketId, String authorEmail) {
        Ticket ticket = mock(Ticket.class);
        when(ticket.getId()).thenReturn(ticketId);
        return new Comment(ticket, new User(authorEmail, "h"), "Original body");
    }

    // Positive: add resolves the ticket + author, trims the body, and sets the author from the
    // principal — not the client.
    @Test
    void addSetsAuthorAndTrimsBody() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = mock(Ticket.class);
        User author = new User("u@example.com", "h");
        when(tickets.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(users.findByEmail("u@example.com")).thenReturn(Optional.of(author));
        when(comments.save(any(Comment.class))).thenAnswer(i -> i.getArgument(0));

        Comment comment = service().add(ticketId, "  Reproduced it  ", "u@example.com");

        assertThat(comment.getBody()).isEqualTo("Reproduced it");
        assertThat(comment.getAuthor()).isSameAs(author);
    }

    // Negative: adding a comment to a non-existent ticket → 404; nothing saved.
    @Test
    void addToMissingTicketThrowsNotFound() {
        UUID ticketId = UUID.randomUUID();
        when(tickets.findById(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().add(ticketId, "body", "u@example.com"))
                .isInstanceOf(NotFoundException.class);
        verify(comments, never()).save(any());
    }

    // Negative: an unresolvable principal → 404; nothing saved.
    @Test
    void addWithUnknownAuthorThrowsNotFound() {
        UUID ticketId = UUID.randomUUID();
        when(tickets.findById(ticketId)).thenReturn(Optional.of(mock(Ticket.class)));
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().add(ticketId, "body", "ghost@example.com"))
                .isInstanceOf(NotFoundException.class);
        verify(comments, never()).save(any());
    }

    // Negative: listing comments for a non-existent ticket → 404.
    @Test
    void listForMissingTicketThrowsNotFound() {
        UUID ticketId = UUID.randomUUID();
        when(tickets.findById(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().list(ticketId)).isInstanceOf(NotFoundException.class);
    }

    // AC-1 (positive): the author edits — body is trimmed and edited_at is stamped from the clock.
    @Test
    void updateByAuthorTrimsBodyAndStampsEditedAt() {
        UUID ticketId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        Comment comment = ownedComment(ticketId, "u@example.com");
        when(comments.findById(commentId)).thenReturn(Optional.of(comment));

        Comment result = service().update(ticketId, commentId, "  Edited body  ", "u@example.com");

        assertThat(result.getBody()).isEqualTo("Edited body");
        assertThat(result.getEditedAt()).isEqualTo(EDIT_TIME);
    }

    // AC-3 (negative): a non-author editing → 403; the body/edited_at are untouched.
    @Test
    void updateByNonAuthorThrowsForbidden() {
        UUID ticketId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        Comment comment = ownedComment(ticketId, "owner@example.com");
        when(comments.findById(commentId)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> service().update(ticketId, commentId, "hijack", "intruder@example.com"))
                .isInstanceOf(CommentAccessDeniedException.class);
        assertThat(comment.getBody()).isEqualTo("Original body");
        assertThat(comment.getEditedAt()).isNull();
    }

    // Negative: editing a comment that exists but under a different ticket → 404 (nested resource).
    @Test
    void updateWithWrongTicketThrowsNotFound() {
        UUID pathTicketId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        Comment comment = ownedComment(UUID.randomUUID(), "u@example.com"); // different ticket
        when(comments.findById(commentId)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> service().update(pathTicketId, commentId, "x", "u@example.com"))
                .isInstanceOf(NotFoundException.class);
    }

    // Boundary/negative: editing a missing comment → 404.
    @Test
    void updateMissingCommentThrowsNotFound() {
        UUID ticketId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        when(comments.findById(commentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().update(ticketId, commentId, "x", "u@example.com"))
                .isInstanceOf(NotFoundException.class);
    }

    // AC-2 (positive): the author deletes their comment.
    @Test
    void deleteByAuthorDeletes() {
        UUID ticketId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        Comment comment = ownedComment(ticketId, "u@example.com");
        when(comments.findById(commentId)).thenReturn(Optional.of(comment));

        service().delete(ticketId, commentId, "u@example.com");

        verify(comments).delete(comment);
    }

    // AC-3 (negative): a non-author deleting → 403; nothing deleted.
    @Test
    void deleteByNonAuthorThrowsForbidden() {
        UUID ticketId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        Comment comment = ownedComment(ticketId, "owner@example.com");
        when(comments.findById(commentId)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> service().delete(ticketId, commentId, "intruder@example.com"))
                .isInstanceOf(CommentAccessDeniedException.class);
        verify(comments, never()).delete(any());
    }

    // Boundary/negative: deleting a missing (or already-deleted) comment → 404.
    @Test
    void deleteMissingCommentThrowsNotFound() {
        UUID ticketId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        when(comments.findById(commentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().delete(ticketId, commentId, "u@example.com"))
                .isInstanceOf(NotFoundException.class);
        verify(comments, never()).delete(any());
    }
}
