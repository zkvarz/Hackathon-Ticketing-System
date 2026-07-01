package com.dataart.tickets.comment;

/**
 * Thrown when a user tries to edit or delete a comment they did not author (HTS-039). Authorship is
 * the only per-row ownership rule in the app — all other data is shared (FR-T6). Mapped to HTTP 403
 * with code FORBIDDEN by the exception handler.
 */
public class CommentAccessDeniedException extends RuntimeException {
    public CommentAccessDeniedException() {
        super("You can only modify your own comments.");
    }
}
