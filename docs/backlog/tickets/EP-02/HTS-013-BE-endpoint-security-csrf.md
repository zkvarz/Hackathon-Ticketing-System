# HTS-013 — [BE] Secure all endpoints except auth set; CSRF; 401/403 error model

| Field | Value |
|-------|-------|
| **ID** | HTS-013 |
| **Type** | BE |
| **Epic** | EP-02 Authentication |
| **Story** | ST-05 Enforcement |
| **Status** | DONE |
| **Depends on** | HTS-011 |
| **Blocks** | HTS-014, HTS-015, HTS-031 |
| **Traceability** | FR-A12; FR-P4, FR-P6; NFR-1; architecture.md §8, §9 |

## Goal
Require authentication on all business endpoints, leaving only the auth set + health + static
assets public, and protect state-changing requests with CSRF — returning proper 401/403 errors.

## Scope
- In scope: Spring Security filter chain authorizing requests; public allowlist
  (`/api/auth/signup`, `/login`, `/logout` is authenticated-or-not per design, `/verify`,
  `/resend`, `/api/health`, static); CSRF protection for non-GET (cookie-to-header token
  suited to the SPA); consistent 401 (unauthenticated) and 403 (forbidden/CSRF) responses
  using the standard error model.
- Out of scope: the global exception-to-error-model mapper (HTS-031, consumed here);
  per-resource authorization (none required — all verified users can manage all data, FR-T6).

## Technical approach
- `SecurityFilterChain` with `authorizeHttpRequests`: permit the allowlist, authenticate the rest.
- CSRF: `CookieCsrfTokenRepository` (or equivalent) so the SPA reads the token and echoes it
  in a header on mutations (architecture.md §9).
- 401/403 entry points/handlers emit the standard error JSON (FR-P4).

## Acceptance criteria
- [x] AC-1 — Unauthenticated request to a business endpoint returns 401 (standard model).
- [x] AC-2 — Public allowlist endpoints work without authentication.
- [x] AC-3 — A state-changing request without a valid CSRF token returns 403.
- [x] AC-4 — An authenticated request with a valid session (and CSRF where needed) succeeds.

## Test plan
**Unit (JUnit 5 + Mockito / Spring Security test):**
- Positive: authenticated principal can reach a protected handler.
- Negative: anonymous → 401; missing CSRF on POST → 403.
- Boundary: each allowlisted path is reachable anonymously; a near-miss path (e.g. `/api/teams`) is not.

**Integration (Testcontainers — Postgres):**
- End-to-end: login (get session + CSRF), call a protected endpoint successfully; repeat without session → 401; mutate without CSRF → 403.

## How to run / verify
```bash
cd backend && ./mvnw test -Dtest='*Security*'
curl -i localhost:8080/api/teams        # expect 401 when unauthenticated
```

## Definition of Done
- [x] AC-1..AC-4 met
- [x] Unit + Testcontainers security tests pass (positive/negative/boundary)
- [x] Allowlist exactly matches FR-A12; no business endpoint left public
- [x] 401/403 use the standard error model
- [x] INDEX.md status updated

## Implementation notes
- `SecurityConfig` (updated from HTS-011): allowlist = `GET /api/health`, `POST
  /api/auth/{signup,login,resend}`, `GET /api/auth/verify`; **everything else authenticated**
  (incl. `/api/auth/me`, `/logout`, and all future business endpoints).
- CSRF on via `CookieCsrfTokenRepository.withHttpOnlyFalse()` + `CsrfTokenRequestAttributeHandler`
  (SPA cookie→header: `XSRF-TOKEN` → `X-XSRF-TOKEN`). The pre-session endpoints
  (signup/login/logout/resend) are CSRF-exempt (no session to protect). `CsrfCookieFilter`
  forces the deferred token to resolve so the cookie is actually written for the SPA.
- 401 `UNAUTHENTICATED` (entry point) and 403 `FORBIDDEN` (access-denied handler, also covers
  CSRF failures) returned in the standard error model.
- Tests (`EndpointSecurityIntegrationTest`, Testcontainers + spring-security-test): 401 on
  unauthenticated business path, health reachable anonymously, authenticated POST without CSRF
  → 403, with `user()`+`csrf()` passes security (404, no controller yet), near-miss path
  requires auth. Existing auth integration tests stay green (their endpoints are exempt/GET);
  the login cookie assertion now selects the JSESSIONID cookie since an XSRF-TOKEN cookie is
  also set.
