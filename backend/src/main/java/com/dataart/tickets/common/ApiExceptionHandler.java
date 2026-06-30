package com.dataart.tickets.common;

import com.dataart.tickets.auth.EmailAlreadyTakenException;
import com.dataart.tickets.auth.TokenInvalidException;
import com.dataart.tickets.team.TeamHasChildrenException;
import com.dataart.tickets.team.TeamNameTakenException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

/**
 * Translates exceptions into the standardized error model (architecture.md §8, FR-P4).
 *
 * <p>This is a focused handler introduced alongside the first endpoints that need it
 * (HTS-005): bean-validation failures → 400 with field errors, and duplicate-email → 409.
 * HTS-031 generalizes this into the full global handler (404/401/403/409 conflict family,
 * etc.); new cases should be added there.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Request validation failed.", fieldErrors);
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

    @ExceptionHandler(TeamNameTakenException.class)
    public ResponseEntity<ApiError> handleTeamNameTaken(TeamNameTakenException ex) {
        return build(HttpStatus.CONFLICT, "NAME_TAKEN", ex.getMessage(), List.of());
    }

    @ExceptionHandler(TeamHasChildrenException.class)
    public ResponseEntity<ApiError> handleTeamHasChildren(TeamHasChildrenException ex) {
        return build(HttpStatus.CONFLICT, "TEAM_HAS_CHILDREN", ex.getMessage(), List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                           List<ApiError.FieldError> fieldErrors) {
        ApiError body = new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), code, message, fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
