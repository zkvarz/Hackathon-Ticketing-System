package com.dataart.tickets.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provides a UTC {@link Clock} so time-dependent logic (e.g. token expiry, FR-A8) is injectable
 * and can be replaced with a fixed clock in tests (architecture.md §12 boundary cases).
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
