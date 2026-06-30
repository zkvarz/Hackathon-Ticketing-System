# HTS-011 — [BE] Login/logout: session cookie, reject unverified, lifetime config

| Field | Value |
|-------|-------|
| **ID** | HTS-011 |
| **Type** | BE |
| **Epic** | EP-02 Authentication |
| **Story** | ST-04 Login/logout |
| **Status** | TODO |
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
- [ ] AC-1 — Verified user with correct password logs in; a session cookie is set (HttpOnly).
- [ ] AC-2 — Unverified user is rejected with 403 `EMAIL_NOT_VERIFIED`.
- [ ] AC-3 — Wrong password / unknown email returns generic 401 `BAD_CREDENTIALS`.
- [ ] AC-4 — Logout invalidates the session; a subsequent `me` call returns 401.
- [ ] AC-5 — Session expires per configured idle/absolute timeouts.

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
- [ ] AC-1..AC-5 met
- [ ] Unit tests (positive/negative/boundary) pass
- [ ] Testcontainers integration (cookie lifecycle, logout, unverified) passes
- [ ] Cookie flags + timeouts match architecture.md §9
- [ ] INDEX.md status updated
