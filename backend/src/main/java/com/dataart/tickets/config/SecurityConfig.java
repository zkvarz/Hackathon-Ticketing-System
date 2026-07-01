package com.dataart.tickets.config;

import com.dataart.tickets.common.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Security configuration (HTS-011 auth, HTS-013 lockdown + CSRF).
 *
 * <p>Public allowlist (FR-A12): health + the pre-session auth endpoints (signup, login, verify,
 * resend). Everything else requires authentication. CSRF uses a cookie-to-header token suited to
 * the SPA ({@code XSRF-TOKEN} cookie → {@code X-XSRF-TOKEN} header); the pre-session/login/logout
 * endpoints are exempt (no session to protect yet). 401/403 are returned in the standard error
 * model (architecture.md §8).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AuthenticationEntryPoint entryPoint,
                                    AccessDeniedHandler accessDeniedHandler,
                                    Clock clock, ObjectMapper mapper,
                                    @Value("${app.session.absolute-timeout}") Duration absoluteTimeout)
            throws Exception {
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        // Pre-session endpoints have no session to protect; logout just clears it.
                        .ignoringRequestMatchers(
                                "/api/auth/signup", "/api/auth/login",
                                "/api/auth/logout", "/api/auth/resend"))
                // Emit the XSRF-TOKEN cookie once the token is resolved.
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                // Enforce the absolute session lifetime cap before authorization (HTS-046): an
                // expired session is invalidated and rejected with the standard 401.
                .addFilterBefore(new SessionAbsoluteTimeoutFilter(absoluteTimeout, clock, mapper),
                        AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/signup", "/api/auth/login", "/api/auth/resend").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/verify").permitAll()
                        // Everything else (incl. /api/auth/me, /logout, and all business endpoints)
                        // requires authentication.
                        .anyRequest().authenticated())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // Security response headers (HTS-033, NFR-1). X-Content-Type-Options: nosniff and
                // X-Frame-Options: DENY are Spring Security defaults; Referrer-Policy is added
                // explicitly. HSTS is emitted only over HTTPS (non-local profiles) by default.
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .referrerPolicy(rp -> rp.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000)))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
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
        return (request, response, ex) ->
                writeError(mapper, response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                        "Authentication is required.");
    }

    /** Forbidden (incl. CSRF failures) → 403 in the standard error model. */
    @Bean
    AccessDeniedHandler restAccessDeniedHandler(ObjectMapper mapper) {
        return (request, response, ex) ->
                writeError(mapper, response, HttpStatus.FORBIDDEN, "FORBIDDEN",
                        "You do not have permission to perform this action.");
    }

    private static void writeError(ObjectMapper mapper, jakarta.servlet.http.HttpServletResponse response,
                                   HttpStatus status, String code, String message) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(),
                code, message, List.of());
        mapper.writeValue(response.getWriter(), body);
    }
}
