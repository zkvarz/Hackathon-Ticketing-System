package com.dataart.tickets.comment;

import com.dataart.tickets.comment.dto.CommentRequest;
import com.dataart.tickets.comment.dto.CommentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Comment endpoints (architecture.md §8, FR-C1..C6). Authenticated. Comments are nested under a
 * ticket; only list + add exist (immutable, no edit/delete — FR-C6). The author is taken from the
 * security context, never the request body.
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
}
