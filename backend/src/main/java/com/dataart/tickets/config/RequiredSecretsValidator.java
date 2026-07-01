package com.dataart.tickets.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Fail-fast guard for required secrets in non-local profiles (HTS-033, NFR-1, DoD-8).
 *
 * <p>The default (local) profile ships developer-friendly defaults so the app boots out of the
 * box. Production must supply every secret from the environment — no baked-in defaults. This
 * validator runs during context initialization under the {@code prod} profile and aborts startup
 * with a single, clear message listing everything missing, rather than failing later with an
 * opaque connection or placeholder error.
 */
@Component
@Profile("prod")
public class RequiredSecretsValidator implements InitializingBean {

    /** Spring property keys that must be supplied (via env) in production. */
    static final List<String> REQUIRED_PROPERTIES = List.of(
            "spring.datasource.url",
            "spring.datasource.username",
            "spring.datasource.password",
            "spring.mail.host");

    private final Environment env;

    public RequiredSecretsValidator(Environment env) {
        this.env = env;
    }

    @Override
    public void afterPropertiesSet() {
        validate(env::getProperty);
    }

    /**
     * Package-private core so it can be unit-tested without a Spring context: given a lookup from
     * property key → value, throws {@link IllegalStateException} if any required key is missing or
     * blank.
     */
    static void validate(java.util.function.Function<String, String> lookup) {
        List<String> missing = REQUIRED_PROPERTIES.stream()
                .filter(key -> !StringUtils.hasText(lookup.apply(key)))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Missing required configuration for the prod profile: " + String.join(", ", missing)
                            + ". Provide these via environment variables before starting.");
        }
    }
}
