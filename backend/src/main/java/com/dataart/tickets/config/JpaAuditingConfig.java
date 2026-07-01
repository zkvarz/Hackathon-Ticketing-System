package com.dataart.tickets.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.util.Optional;

/**
 * Enables Spring Data JPA Auditing and sources its "now" from the application {@link Clock}
 * (HTS-047). Because {@code @CreatedDate}/{@code @LastModifiedDate} on {@code BaseEntity} draw from
 * this single provider, entity timestamps are deterministic under a fixed clock and identical to the
 * instant the service layer observes — no static state, scoped per Spring context.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }
}
