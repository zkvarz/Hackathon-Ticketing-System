package com.dataart.tickets.auth;

import com.dataart.tickets.auth.dto.ResetPasswordRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bean-validation boundary tests for the reset-password request (HTS-037): new-password length
 * 8..128 (FR-A4 / AMB-1) and required token. Pure unit test — no Spring context.
 */
class ResetPasswordValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    // Boundary: 7 rejected, 8 accepted, 128 accepted, 129 rejected.
    @ParameterizedTest
    @CsvSource({
            "7,  true",
            "8,  false",
            "128, false",
            "129, true",
    })
    void passwordLengthBoundaries(int length, boolean expectViolation) {
        var request = new ResetPasswordRequest("tok", "a".repeat(length));

        boolean hasPasswordViolation = validator.validate(request).stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("password"));

        assertThat(hasPasswordViolation).isEqualTo(expectViolation);
    }

    // Negative: a blank token is rejected.
    @Test
    void blankTokenRejected() {
        var request = new ResetPasswordRequest("   ", "password1");

        boolean hasTokenViolation = validator.validate(request).stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("token"));

        assertThat(hasTokenViolation).isTrue();
    }

    // Positive: a well-formed request has no violations.
    @Test
    void validRequestHasNoViolations() {
        var request = new ResetPasswordRequest("tok", "password1");
        assertThat(validator.validate(request)).isEmpty();
    }
}
