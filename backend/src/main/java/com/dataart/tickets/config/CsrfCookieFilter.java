package com.dataart.tickets.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Forces the deferred {@link CsrfToken} to be resolved on each request so the
 * {@code XSRF-TOKEN} cookie is actually written to the response. Spring Security 6 loads the
 * CSRF token lazily; without this the SPA would never receive the cookie it needs to echo back
 * in the {@code X-XSRF-TOKEN} header on mutations (HTS-013, architecture.md §9).
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            // Touch the token value to trigger the repository to set the cookie.
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
