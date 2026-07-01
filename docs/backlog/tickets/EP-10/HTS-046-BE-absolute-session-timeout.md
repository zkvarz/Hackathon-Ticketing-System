# HTS-046 — [BE] Absolute session lifetime cap (8h), independent of idle timeout

| Field | Value |
|-------|-------|
| **ID** | HTS-046 |
| **Type** | BE (tech-debt / security) |
| **Epic** | EP-10 Improvements & Tech Debt |
| **Story** | ST-03 Session integrity |
| **Status** | TODO |
| **Depends on** | HTS-011 |
| **Blocks** | — |
| **Traceability** | AMB-7 / A-3; NFR-1; architecture.md §9 |

## Goal
Enforce an **absolute** maximum session lifetime (default 8h) so a session expires that long
after login regardless of activity — not just after an idle gap.

## Context / why
The servlet container only models an **idle/sliding** timeout
(`server.servlet.session.timeout`, currently 30m). AMB-7 / A-3 calls for *both* an idle timeout
**and** an absolute cap (8h), and the config already carries the intended value
(`app.session.absolute-timeout: ${APP_SESSION_TIMEOUT_ABSOLUTE:8h}`) — but nothing reads it. A
continuously-active session can therefore live indefinitely, which the absolute cap is meant to
prevent. This was flagged (and deferred) during HTS-011/HTS-033; it is a small, isolated
hardening item, not a spec gap in behavior the app claims to have.

## Scope
- In scope: a small filter (or `HttpSessionListener` + per-request check) that stamps the login
  instant on the session and invalidates it once `now - createdAt >= app.session.absolute-timeout`,
  returning the standard `401 UNAUTHENTICATED` error model so the FE 401 handler (HTS-014)
  redirects to login. Driven by an injected `Clock` for deterministic tests.
- Out of scope: idle timeout (already handled by the container); changing the FR-A7/login flow;
  "remember me" / refresh semantics (not in scope for the app).

## Technical approach
- On successful authentication, record `SESSION_CREATED_AT` (an `Instant`) in the session.
- A `OncePerRequestFilter` (ordered before the controller, after security) reads that attribute;
  if `Duration.between(createdAt, clock.instant()) >= absoluteTimeout`, invalidate the session
  and short-circuit with the standard `ApiError` 401 (`UNAUTHENTICATED`).
- Read `app.session.absolute-timeout` (already present) via `@ConfigurationProperties`/`@Value`;
  keep `Clock` injectable so tests can fast-forward.

## Acceptance criteria
- [x] AC-1 — A session older than the absolute cap is rejected (401 in the standard error model)
  even with continuous activity.
- [x] AC-2 — A session within the cap keeps working normally; the idle timeout still applies too.
- [x] AC-3 — The cap value comes from `app.session.absolute-timeout` (env-overridable).
- [x] AC-4 — Expiry surfaces as `401 UNAUTHENTICATED` (same shape the FE already redirects on).

## Test plan
**Unit (JUnit 5 + Mockito):**
- Positive: with a fixed clock, a request just under the cap passes; just over the cap invalidates.
- Negative: no `SESSION_CREATED_AT` (anonymous / pre-session) → filter is a no-op.
- Boundary: exactly at the cap boundary; env override changes the threshold.

**Integration (Testcontainers — Postgres):**
- Log in, advance the injected clock past the cap, and assert the next request returns
  `401 UNAUTHENTICATED` and the session is invalidated.

## How to run / verify
```bash
cd backend && ./mvnw test -Dtest='*Session*'
```

## Definition of Done
- [x] AC-1..AC-4 met
- [x] Unit + integration tests pass (positive/negative/boundary) — `SessionAbsoluteTimeoutFilterTest`
  (6) + `SessionAbsoluteTimeoutIntegrationTest` (2)
- [x] `application.yml` comment updated to reflect the cap is now enforced (not a TODO)
- [x] architecture.md §9 note on session lifetime updated
- [x] INDEX.md status updated

## Implementation notes (as built)
- `SessionAbsoluteTimeoutFilter` (config, `OncePerRequestFilter`): reads the `SESSION_CREATED_AT`
  instant stamped on the session and, once `now − createdAt ≥ app.session.absolute-timeout`,
  invalidates the session + clears the context + writes the standard `401 UNAUTHENTICATED`
  `ApiError`. Clock-injected; boundary is "elapsed ≥ cap" (exactly at cap expires).
- Wired in `SecurityConfig.filterChain` via `addFilterBefore(..., AuthorizationFilter.class)` with
  the cap bound from `@Value("${app.session.absolute-timeout}")` (Duration). The filter is
  constructed (not a `@Component`) so Boot doesn't also register it on the plain servlet chain.
- `AuthController.login` stamps `SESSION_CREATED_AT = clock.instant()` on the session right after
  `saveContext`. `AuthController` now takes the `Clock` bean.
- Test note: the integration test overrides the `clock` bean with a fast-forwardable `MutableClock`
  (`spring.main.allow-bean-definition-overriding=true`) to advance past the cap deterministically.
