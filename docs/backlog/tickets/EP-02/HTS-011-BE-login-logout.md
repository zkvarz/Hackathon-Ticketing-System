# HTS-011 — [BE] Login/logout: session cookie, reject unverified, lifetime config

| Field | Value |
|-------|-------|
| **ID** | HTS-011 |
| **Type** | BE |
| **Epic** | EP-02 Authentication |
| **Story** | ST-04 Login/logout |
| **Status** | DONE |
| **Depends on** | HTS-007 |
| **Blocks** | HTS-012, HTS-013 |
| **Traceability** | FR-A3, FR-A7; AMB-7; DoD-1; architecture.md §9 |

## Goal
Authenticate verified users with email/password, establish a server-side session cookie, and
support logout that invalidates the session. Block unverified users.

## Scope
- In scope: `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`; Argon2id
  password verification; session creation with `HttpOnly`/`Secure`/`SameSite` cookie; session
  lifetime (8h absolute + 30 min idle, env-driven); reject unverified accounts.
- Out of scope: blanket endpoint protection + CSRF wiring (HTS-013); FE screens (HTS-012).

## Technical approach
- Spring Security config: form/JSON login authenticating against the user store using
  `Argon2PasswordEncoder`.
- Session via Spring Session (or servlet session) with cookie flags per architecture.md §9;
  timeouts from `APP_SESSION_TIMEOUT_*`.
- Unverified user → 403 `EMAIL_NOT_VERIFIED` (not 401) so the FE can offer resend.
- Bad credentials → 401 `BAD_CREDENTIALS` (generic, no user/pass distinction).
- Logout invalidates the server session and clears the cookie (FR-A3).
- `GET /api/auth/me` returns the current user (email) for the FE auth context.

## Acceptance criteria
- [x] AC-1 — Verified user with correct password logs in; a session cookie is set (HttpOnly).
- [x] AC-2 — Unverified user is rejected with 403 `EMAIL_NOT_VERIFIED`.
- [x] AC-3 — Wrong password / unknown email returns generic 401 `BAD_CREDENTIALS`.
- [x] AC-4 — Logout invalidates the session; a subsequent `me` call returns 401.
- [~] AC-5 — Idle/sliding timeout configured (`server.servlet.session.timeout` = 30m via
  `APP_SESSION_TIMEOUT_IDLE`). **Absolute 8h cap is a documented follow-up** (servlet sessions
  only model idle timeout; needs a custom filter) — tracked for HTS-033.

## Test plan
**Unit (JUnit 5 + Mockito):**
- Positive: correct credentials + verified → authentication success.
- Negative: wrong password → failure; unverified → verification-required failure.
- Boundary: email case/whitespace normalization still authenticates the right user; empty password rejected.

**Integration (Testcontainers — Postgres):**
- Full login → `Set-Cookie` present with HttpOnly/SameSite; `GET /api/auth/me` returns the user using that cookie.
- Logout → cookie cleared/invalidated; `me` then returns 401.
- Unverified user → 403 with the right code.

## How to run / verify
```bash
cd backend && ./mvnw test -Dtest='*Login*'
curl -i -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"a@b.com","password":"password1"}'
```

## Definition of Done
- [x] AC-1..AC-4 met; AC-5 idle timeout configured (absolute cap deferred — see above)
- [x] Unit tests (positive/negative/boundary) pass
- [x] Testcontainers integration (cookie lifecycle, logout, unverified) passes
- [x] Cookie flags + timeouts match architecture.md §9 (HttpOnly + SameSite=Lax; Secure via
  non-local profile in HTS-033)
- [x] INDEX.md status updated

## Implementation notes
- Added `spring-boot-starter-security` (+ `spring-security-test`). `config/SecurityConfig`:
  `SecurityFilterChain` with **CSRF disabled (deferred to HTS-013)**, only `/api/auth/me`
  authenticated, everything else `permitAll` (so signup/verify/resend/health keep working);
  `AuthenticationManager` (DaoAuthenticationProvider + Argon2 encoder); a
  `HttpSessionSecurityContextRepository`; and a REST `AuthenticationEntryPoint` returning 401
  `UNAUTHENTICATED` in the standard error model.
- `AppUserDetailsService`: loads by normalized email; **unverified → disabled** so auth fails
  with `DisabledException`. `AuthController` adds custom JSON `POST /login` (saves context to
  session), `GET /me`, `POST /logout` (session invalidate + clear context). Exception handler
  maps `DisabledException` → 403 `EMAIL_NOT_VERIFIED`, `BadCredentialsException` → 401
  `BAD_CREDENTIALS`.
- `application.yml`: `server.servlet.session.timeout` (idle) + cookie `http-only`/`same-site=lax`.
- `HealthControllerTest` (@WebMvcTest) got `@AutoConfigureMockMvc(addFilters=false)` so the
  new security default doesn't 401 the slice.
- Tests (42 total green): `AppUserDetailsServiceTest` (Mockito: verified/unverified/normalize/
  unknown) + `LoginLogoutIntegrationTest` (RANDOM_PORT TestRestTemplate for the real cookie
  lifecycle + MockMvc for the POST→401 credential cases, sidestepping a JDK HttpURLConnection
  retry quirk on 401-to-streamed-POST).
