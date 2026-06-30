package com.dataart.tickets.config;

import com.dataart.tickets.common.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import java.time.Instant;
import java.util.List;

/**
 * Security configuration (HTS-011). Establishes session-based authentication and the
 * login/me protection. CSRF and the full public-allowlist lockdown of business endpoints are
 * intentionally deferred to HTS-013; here CSRF is disabled and only {@code /api/auth/me} is
 * guarded, so the existing public endpoints (signup/verify/resend/health) keep working.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AuthenticationEntryPoint entryPoint)
            throws Exception {
        http
                // CSRF is wired with a cookie-to-header repository in HTS-013.
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/me").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(eh -> eh.authenticationEntryPoint(entryPoint))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // Logout is handled by a custom JSON endpoint in AuthController.
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** Unauthenticated access to a protected endpoint → 401 in the standard error model. */
    @Bean
    AuthenticationEntryPoint restAuthenticationEntryPoint(ObjectMapper mapper) {
        return (request, response, ex) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiError body = new ApiError(Instant.now(), HttpStatus.UNAUTHORIZED.value(),
                    "Unauthorized", "UNAUTHENTICATED", "Authentication is required.", List.of());
            mapper.writeValue(response.getWriter(), body);
        };
    }
}
