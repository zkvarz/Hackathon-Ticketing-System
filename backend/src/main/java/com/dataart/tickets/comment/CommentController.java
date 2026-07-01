package com.dataart.tickets.comment;

import com.dataart.tickets.comment.dto.CommentRequest;
import com.dataart.tickets.comment.dto.CommentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Comment endpoints (architecture.md §8, FR-C1..C6; HTS-039 stretch). Authenticated. Comments are
 * nested under a ticket. List + add are the base feature; the EP-09 stretch adds author-only
 * edit/delete on {@code /{commentId}}. The author/requester is always taken from the security
 * context, never the request body.
 */
@RestController
@RequestMapping("/api/tickets/{ticketId}/comments")
public class CommentController {

    private final CommentService comments;

    public CommentController(CommentService comments) {
        this.comments = comments;
    }

    @GetMapping
    public List<CommentResponse> list(@PathVariable UUID ticketId) {
        return comments.list(ticketId).stream().map(CommentResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse add(@PathVariable UUID ticketId, @Valid @RequestBody CommentRequest request,
                               Authentication authentication) {
        return CommentResponse.from(comments.add(ticketId, request.body(), authentication.getName()));
    }

    /** Edit the author's own comment (HTS-039). Non-author → 403; missing → 404. */
    @PutMapping("/{commentId}")
    public CommentResponse update(@PathVariable UUID ticketId, @PathVariable UUID commentId,
                                  @Valid @RequestBody CommentRequest request,
                                  Authentication authentication) {
        return CommentResponse.from(
                comments.update(ticketId, commentId, request.body(), authentication.getName()));
    }

    /** Delete the author's own comment (HTS-039). Non-author → 403; missing → 404. */
    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID ticketId, @PathVariable UUID commentId,
                       Authentication authentication) {
        comments.delete(ticketId, commentId, authentication.getName());
    }
}
