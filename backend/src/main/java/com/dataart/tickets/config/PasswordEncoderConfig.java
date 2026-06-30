package com.dataart.tickets.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Provides the Argon2id password encoder (AMB-2 / FR-A5). Uses Spring Security's recommended
 * current parameters. Defined standalone (no security auto-config) since endpoint security /
 * CSRF land later in HTS-013.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
