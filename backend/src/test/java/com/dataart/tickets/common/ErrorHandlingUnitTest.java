package com.dataart.tickets.common;

import com.dataart.tickets.epic.EpicHasTicketsException;
import com.dataart.tickets.team.TeamHasChildrenException;
import com.dataart.tickets.team.TeamNameTakenException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.NoHandlerFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ApiExceptionHandler} (HTS-031). Exercises the advice in isolation — no
 * Spring context — asserting each exception maps to the expected status + stable code, that an
 * unmapped exception is a safe generic 500, and that multi-field validation lists every field.
 */
class ErrorHandlingUnitTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    // Positive: representative exceptions each map to their documented status + code.
    @Test
    void mapsDomainExceptionsToExpectedStatusAndCode() {
        assertThat(handler.handleNotFound(new NotFoundException("nope")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertCode(handler.handleNotFound(new NotFoundException("nope")), "NOT_FOUND");

        ResponseEntity<ApiError> conflict = handler.handleTeamNameTaken(new TeamNameTakenException("taken"));
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertCode(conflict, "NAME_TAKEN");

        assertCode(handler.handleTeamHasChildren(new TeamHasChildrenException()), "TEAM_HAS_CHILDREN");
        assertCode(handler.handleEpicHasTickets(new EpicHasTicketsException()), "EPIC_HAS_TICKETS");

        ResponseEntity<ApiError> unreadable = handler.handleUnreadable(
                new HttpMessageNotReadableException("bad", (org.springframework.http.HttpInputMessage) null));
        assertThat(unreadable.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertCode(unreadable, "VALIDATION_FAILED");
    }

    // Negative: an exception the advice does not explicitly map falls through to the catch-all →
    // 500 with a generic message and no field errors (no stack/secret leakage, AC-4).
    @Test
    void unmappedExceptionBecomesGeneric500() {
        ResponseEntity<ApiError> res = handler.handleUnexpected(
                new IllegalStateException("secret db password = hunter2"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ApiError body = res.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.message()).doesNotContain("hunter2").doesNotContain("secret");
        assertThat(body.fieldErrors()).isEmpty();
    }

    // Boundary: bean validation with several field errors lists every one; a missing required
    // param names that param as a single field error.
    @Test
    void validationListsAllFieldErrorsAndMissingParamNamesIt() throws Exception {
        BindingResult binding = new BeanPropertyBindingResult(new Object(), "req");
        binding.addError(new FieldError("req", "title", "must not be blank"));
        binding.addError(new FieldError("req", "teamId", "must not be null"));
        MethodParameter param = new MethodParameter(
                ErrorHandlingUnitTest.class.getDeclaredMethod(
                        "validationListsAllFieldErrorsAndMissingParamNamesIt"), -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, binding);

        ApiError body = handler.handleValidation(ex).getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(body.fieldErrors()).extracting(ApiError.FieldError::field)
                .containsExactlyInAnyOrder("title", "teamId");

        ApiError missing = handler.handleMissingParam(
                new MissingServletRequestParameterException("teamId", "UUID")).getBody();
        assertThat(missing).isNotNull();
        assertThat(missing.fieldErrors()).singleElement()
                .extracting(ApiError.FieldError::field).isEqualTo("teamId");
    }

    // Boundary: an unknown route maps to 404 in the model (not Boot's Whitelabel).
    @Test
    void noHandlerBecomes404() {
        ResponseEntity<ApiError> res = handler.handleNoHandler(
                new NoHandlerFoundException("GET", "/api/nope", null));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertCode(res, "NOT_FOUND");
    }

    private static void assertCode(ResponseEntity<ApiError> res, String code) {
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().code()).isEqualTo(code);
    }
}
