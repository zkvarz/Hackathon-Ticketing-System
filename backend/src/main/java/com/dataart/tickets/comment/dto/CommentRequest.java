package com.dataart.tickets.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Add-comment body (FR-C1/C3). Body is trimmed, non-empty, and bounded to the same 10000-char
 * limit as ticket bodies (AMB-1) for consistency. The author comes from the security context.
 */
public record CommentRequest(
        @NotBlank(message = "Comment body is required.")
        @Size(max = 10000, message = "Comment must be at most 10000 characters.")
        String body
) {
    public CommentRequest {
        if (body != null) {
            body = body.trim();
        }
    }
}
