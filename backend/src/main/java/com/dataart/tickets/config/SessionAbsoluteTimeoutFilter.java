package com.dataart.tickets.config;

import com.dataart.tickets.common.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Enforces an <em>absolute</em> maximum session lifetime (HTS-046, AMB-7 / A-3). The servlet
 * container only models an idle/sliding timeout ({@code server.servlet.session.timeout}); this
 * filter additionally kills a session once it is older than {@code app.session.absolute-timeout}
 * regardless of activity, so a continuously-active session cannot live forever.
 *
 * <p>Login stamps {@link #SESSION_CREATED_AT} (an {@link Instant} from the injected {@link Clock})
 * on the session; this filter compares it against {@code clock.instant()} and, once the cap is
 * reached, invalidates the session and short-circuits with the standard {@code 401 UNAUTHENTICATED}
 * error body — the same shape the FE 401 handler (HTS-014) already redirects on. The clock is
 * injected so tests can fast-forward deterministically.
 *
 * <p>Deliberately not a Spring bean: it is constructed in {@link SecurityConfig} and added to the
 * security chain, so Boot does not also auto-register it on the plain servlet filter chain.
 */
public class SessionAbsoluteTimeoutFilter extends OncePerRequestFilter {

    /** Session attribute holding the login instant used to enforce the absolute cap. */
    public static final String SESSION_CREATED_AT = "SESSION_CREATED_AT";

    private final Duration absoluteTimeout;
    private final Clock clock;
    private final ObjectMapper mapper;

    public SessionAbsoluteTimeoutFilter(Duration absoluteTimeout, Clock clock, ObjectMapper mapper) {
        this.absoluteTimeout = absoluteTimeout;
        this.clock = clock;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null
                && session.getAttribute(SESSION_CREATED_AT) instanceof Instant createdAt
                && isExpired(createdAt)) {
            // Absolute cap reached: kill the session and short-circuit with the standard 401 so the
            // FE redirects to login (independent of the container idle timeout).
            SecurityContextHolder.clearContext();
            session.invalidate();
            writeUnauthenticated(response);
            return;
        }
        chain.doFilter(request, response);
    }

    // Expired once elapsed >= cap, so exactly at the boundary counts as expired.
    private boolean isExpired(Instant createdAt) {
        return !Duration.between(createdAt, clock.instant()).minus(absoluteTimeout).isNegative();
    }

    private void writeUnauthenticated(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = new ApiError(clock.instant(), HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(), "UNAUTHENTICATED",
                "Your session has expired. Please sign in again.", List.of());
        mapper.writeValue(response.getWriter(), body);
    }
}
