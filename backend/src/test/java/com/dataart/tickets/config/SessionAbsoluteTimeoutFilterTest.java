package com.dataart.tickets.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static com.dataart.tickets.config.SessionAbsoluteTimeoutFilter.SESSION_CREATED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for the absolute session-lifetime filter (HTS-046) with a fixed clock: within the cap
 * requests pass through untouched; at/over the cap the session is invalidated and a 401
 * UNAUTHENTICATED body is written without calling the chain; no stamp / no session is a no-op; and
 * the configured threshold (not a hard-coded 8h) determines expiry.
 */
class SessionAbsoluteTimeoutFilterTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Duration CAP = Duration.ofHours(8);

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final SessionAbsoluteTimeoutFilter filter =
            new SessionAbsoluteTimeoutFilter(CAP, clock, mapper);

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setup() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    // Positive: a session comfortably within the cap passes through unchanged.
    @Test
    void withinCapPassesThrough() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SESSION_CREATED_AT, NOW.minus(Duration.ofHours(1)));
        request.setSession(session);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(session.isInvalid()).isFalse();
    }

    // Negative: a session past the cap is invalidated and rejected with 401; the chain is skipped.
    @Test
    void overCapRejectsWith401() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SESSION_CREATED_AT, NOW.minus(CAP).minusSeconds(1));
        request.setSession(session);

        filter.doFilter(request, response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHENTICATED");
        assertThat(session.isInvalid()).isTrue();
    }

    // Boundary: exactly at the cap counts as expired (elapsed >= cap).
    @Test
    void exactlyAtCapRejects() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SESSION_CREATED_AT, NOW.minus(CAP));
        request.setSession(session);

        filter.doFilter(request, response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(401);
    }

    // Negative: no session at all → the filter is a no-op.
    @Test
    void noSessionPassesThrough() throws Exception {
        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // Negative: a session that was never stamped (e.g. pre-login) → no-op.
    @Test
    void sessionWithoutStampPassesThrough() throws Exception {
        request.setSession(new MockHttpSession());

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // Boundary: the configured threshold (here 1h), not a fixed 8h, decides expiry (AC-3).
    @Test
    void configuredThresholdApplies() throws Exception {
        SessionAbsoluteTimeoutFilter shortFilter =
                new SessionAbsoluteTimeoutFilter(Duration.ofHours(1), clock, mapper);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SESSION_CREATED_AT, NOW.minus(Duration.ofMinutes(90)));
        request.setSession(session);

        shortFilter.doFilter(request, response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(401);
    }
}
