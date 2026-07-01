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
 * Unit tests for comment business logic (HTS-023). Covers author-from-principal, body trimming,
 * and the 404s for a missing ticket or principal.
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository comments;
    @Mock
    private TicketRepository tickets;
    @Mock
    private UserRepository users;

    private CommentService service() {
        return new CommentService(comments, tickets, users);
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
}
