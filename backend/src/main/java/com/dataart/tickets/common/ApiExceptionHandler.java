package com.dataart.tickets.common;

import com.dataart.tickets.auth.EmailAlreadyTakenException;
import org.springframework.http.HttpStatus;
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

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                           List<ApiError.FieldError> fieldErrors) {
        ApiError body = new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), code, message, fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
