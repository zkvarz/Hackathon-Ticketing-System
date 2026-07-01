package com.dataart.tickets.common;

import com.dataart.tickets.auth.EmailAlreadyTakenException;
import com.dataart.tickets.auth.TokenInvalidException;
import com.dataart.tickets.comment.CommentAccessDeniedException;
import com.dataart.tickets.epic.EpicHasTicketsException;
import com.dataart.tickets.epic.EpicTeamImmutableException;
import com.dataart.tickets.team.TeamHasChildrenException;
import com.dataart.tickets.team.TeamNameTakenException;
import com.dataart.tickets.ticket.EpicTeamMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;

/**
 * Global exception → standardized error model translation (architecture.md §8, FR-P4, NFR-1).
 *
 * <p>This is the single {@code @RestControllerAdvice} for the app (HTS-031 consolidated the
 * focused handler that grew up with the auth/team/epic/ticket endpoints). Every mapped
 * exception yields the same JSON shape ({@link ApiError}) with a stable machine-readable
 * {@code code} the frontend branches on — keep existing codes stable.
 *
 * <p>Coverage: bean-validation and request-binding failures → 400; the domain conflict family
 * → 409; not-found → 404; auth/CSRF → 401/403 (401/403 for the security filter chain itself are
 * produced by {@code SecurityConfig}'s entry-point/access-denied handlers, since those fire
 * before this advice); and a catch-all → 500 with a safe generic message (no stack/secret
 * leakage). Adding a new domain error is one {@code @ExceptionHandler} here.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Request validation failed.", fieldErrors);
    }

    // Malformed JSON or an unparseable value (e.g. an unknown ticket type/state enum) → 400. The
    // specific cause is not echoed back to avoid leaking internals; field-level detail comes from
    // bean validation above where applicable.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Request body is malformed or contains an invalid value.", List.of());
    }

    // A path/query parameter that can't be bound to its target type — e.g. an unknown ticket type
    // in the board filter, or a malformed UUID → 400.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "A request parameter has an invalid value.", List.of());
    }

    // A required query/form parameter was omitted (e.g. the board's mandatory `teamId`). Without
    // this handler Spring returns a bare 400 outside our model; here it joins the model with the
    // missing parameter named as a field error.
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "A required request parameter is missing.",
                List.of(new ApiError.FieldError(ex.getParameterName(), "is required")));
    }

    @ExceptionHandler(EmailAlreadyTakenException.class)
    public ResponseEntity<ApiError> handleEmailTaken(EmailAlreadyTakenException ex) {
        return build(HttpStatus.CONFLICT, "EMAIL_TAKEN",
                "An account with this email already exists.", List.of());
    }

    @ExceptionHandler(TokenInvalidException.class)
    public ResponseEntity<ApiError> handleTokenInvalid(TokenInvalidException ex) {
        return build(HttpStatus.BAD_REQUEST, "TOKEN_INVALID", ex.getMessage(), List.of());
    }

    // Login: an unverified account is "disabled" (AppUserDetailsService) → 403 so the FE can
    // offer a resend, distinct from bad credentials (FR-A7).
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiError> handleDisabled(DisabledException ex) {
        return build(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED",
                "This account's email address is not verified.", List.of());
    }

    // Wrong password or unknown email → one generic 401 (no field-level distinction).
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS",
                "Invalid email or password.", List.of());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), List.of());
    }

    // Editing/deleting a comment you did not author → 403 (HTS-039). Reuses the FORBIDDEN code the
    // FE already treats as "not permitted", distinct from the 401 unauthenticated path.
    @ExceptionHandler(CommentAccessDeniedException.class)
    public ResponseEntity<ApiError> handleCommentAccessDenied(CommentAccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage(), List.of());
    }

    @ExceptionHandler(TeamNameTakenException.class)
    public ResponseEntity<ApiError> handleTeamNameTaken(TeamNameTakenException ex) {
        return build(HttpStatus.CONFLICT, "NAME_TAKEN", ex.getMessage(), List.of());
    }

    @ExceptionHandler(TeamHasChildrenException.class)
    public ResponseEntity<ApiError> handleTeamHasChildren(TeamHasChildrenException ex) {
        return build(HttpStatus.CONFLICT, "TEAM_HAS_CHILDREN", ex.getMessage(), List.of());
    }

    @ExceptionHandler(EpicTeamImmutableException.class)
    public ResponseEntity<ApiError> handleEpicTeamImmutable(EpicTeamImmutableException ex) {
        return build(HttpStatus.BAD_REQUEST, "EPIC_TEAM_IMMUTABLE", ex.getMessage(), List.of());
    }

    @ExceptionHandler(EpicTeamMismatchException.class)
    public ResponseEntity<ApiError> handleEpicTeamMismatch(EpicTeamMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "EPIC_TEAM_MISMATCH", ex.getMessage(), List.of());
    }

    @ExceptionHandler(EpicHasTicketsException.class)
    public ResponseEntity<ApiError> handleEpicHasTickets(EpicHasTicketsException ex) {
        return build(HttpStatus.CONFLICT, "EPIC_HAS_TICKETS", ex.getMessage(), List.of());
    }

    // An unknown route (no controller mapping / no static resource) → 404 in our model rather
    // than Boot's Whitelabel page. NoHandlerFoundException requires
    // spring.mvc.throw-exception-if-no-handler-found=true (set in application.yml); modern Boot
    // also throws NoResourceFoundException for unmatched paths under the resource handler.
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> handleNoHandler(Exception ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "No handler for this request.", List.of());
    }

    // Catch-all (AC-4): any unmapped exception → 500 with a safe generic message. The real cause
    // is logged server-side but never echoed to the client (no stack trace / secret leakage, NFR-1).
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred.", List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                           List<ApiError.FieldError> fieldErrors) {
        ApiError body = new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), code, message, fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
