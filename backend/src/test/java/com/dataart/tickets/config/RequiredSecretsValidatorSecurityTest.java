package com.dataart.tickets.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fail-fast secret validation for the prod profile (HTS-033). Exercises the validator core so a
 * missing/blank required secret aborts startup with a clear, aggregated message.
 */
class RequiredSecretsValidatorSecurityTest {

    private static Map<String, String> fullEnv() {
        Map<String, String> env = new HashMap<>();
        RequiredSecretsValidator.REQUIRED_PROPERTIES.forEach(k -> env.put(k, "value"));
        return env;
    }

    // Positive: every required secret present → no exception.
    @Test
    void allSecretsPresentPasses() {
        Map<String, String> env = fullEnv();
        assertThatCode(() -> RequiredSecretsValidator.validate(env::get)).doesNotThrowAnyException();
    }

    // Negative: a missing secret → startup aborts with a message naming it.
    @Test
    void missingSecretFailsWithClearMessage() {
        Map<String, String> env = fullEnv();
        env.remove("spring.datasource.password");

        assertThatThrownBy(() -> RequiredSecretsValidator.validate(env::get))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.password")
                .hasMessageContaining("environment variables");
    }

    // Boundary: a blank (whitespace) secret counts as missing, and multiple missing are all listed.
    @Test
    void blankSecretIsMissingAndAllAreListed() {
        Map<String, String> env = fullEnv();
        env.put("spring.datasource.password", "   ");
        env.remove("spring.mail.host");

        assertThatThrownBy(() -> RequiredSecretsValidator.validate(env::get))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(ex -> assertThat(ex.getMessage())
                        .contains("spring.datasource.password")
                        .contains("spring.mail.host"));
    }
}
