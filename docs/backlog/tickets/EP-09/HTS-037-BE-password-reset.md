# HTS-037 — [BE] Password reset flow (backend)

| Field | Value |
|-------|-------|
| **ID** | HTS-037 |
| **Type** | BE |
| **Epic** | EP-09 Stretch |
| **Story** | ST-01 Password reset |
| **Status** | DONE |
| **Priority** | Optional / stretch |
| **Depends on** | HTS-011 |
| **Blocks** | HTS-038 |
| **Traceability** | Stretch §2.10; mirrors FR-A5/A8 patterns; architecture.md §9, §10 |

## Goal
Let a user reset a forgotten password via an emailed single-use, time-limited token, reusing
the verification/email and Argon2id hashing patterns.

## Scope
- In scope: `POST /api/auth/forgot-password` (issue token + email via Mailpit), `POST
  /api/auth/reset-password` (validate token, set new Argon2id hash); single-use + TTL; invalidate
  prior reset tokens on reissue; no account enumeration.
- Out of scope: FE (HTS-038).

## Technical approach
- Reuse the token + mail infrastructure from EP-02 (separate token type/purpose).
- New password validated to the same rules (8..128, AMB-1); hashed with Argon2id (AMB-2).
- Generic responses regardless of email existence (no enumeration).

## Acceptance criteria
- [x] AC-1 — Forgot-password issues a token and sends an email (Mailpit) for an existing user.
- [x] AC-2 — Reset with a valid token sets a new hash; old password no longer works, new one does.
- [x] AC-3 — Expired/consumed/unknown reset token → rejected.
- [x] AC-4 — Forgot-password for unknown email returns the same generic success (no enumeration).

## Test plan
**Unit (JUnit + Mockito):** positive (valid reset), negative (expired/consumed/unknown token), boundary (new password length 7/8/128/129; token at expiry edge with fixed clock).
**Integration (Testcontainers — Postgres + Mailpit):** forgot → read token from Mailpit → reset → login with new password succeeds, old fails; reused token rejected.

## How to run / verify
```bash
cd backend && ./mvnw test -Dtest='*PasswordReset*'
```

## Definition of Done
- [x] AC-1..AC-4 met; unit + integration tests pass (positive/negative/boundary)
- [x] No enumeration; INDEX.md status updated

## Implementation notes (as built)
- New `password_reset_tokens` table (`V10__password_reset_tokens.sql`), mirroring V3
  (`email_verification_tokens`) — a distinct table/type so reset and verification tokens never
  share a space. Entity `PasswordResetToken extends BaseEntity`; repo
  `PasswordResetTokenRepository` (`findByToken`, `findByUserAndConsumedAtIsNull`).
- `PasswordResetService`: `requestReset(email)` invalidates prior unused tokens, issues a fresh
  single-use token (`SecureRandom`, 32 bytes, base64url), and emails a `/reset-password?token=…`
  link (best-effort send, token persisted first); `reset(token, newPassword)` validates
  existence/unconsumed/unexpired, sets a new **Argon2id** hash (`PasswordEncoder.encode`), consumes
  the token, and invalidates any siblings. TTL `app.password-reset.token-ttl` (default **1h**,
  shorter than verification's 24h).
- Endpoints on `AuthController` (public, CSRF-exempt, no session created): `POST
  /api/auth/forgot-password` → **202** generic `{"status":"sent"}` regardless of email existence
  (no enumeration); `POST /api/auth/reset-password` → **200** `{"status":"reset"}`. Both added to
  the `SecurityConfig` permit-list + CSRF ignore-list.
- Errors reuse the existing model: invalid/expired/consumed token → **400 `TOKEN_INVALID`**
  (`TokenInvalidException` gained a message-taking constructor so the reset flow reports a
  reset-specific message under the same stable code); password length 8..128 enforced on
  `ResetPasswordRequest` → **400 `VALIDATION_FAILED`**.
- `User.setPasswordHash(...)` added (accepts an already-hashed value).
- Config: `app.password-reset.token-ttl` in `application.yml`; `APP_PASSWORD_RESET_TOKEN_TTL` in
  `.env.example`.
- Tests (15): `PasswordResetServiceTest` (7 — request rotation/no-op/unknown; reset
  positive/unknown/consumed/at-expiry), `ResetPasswordValidationTest` (6 — length 7/8/128/129,
  blank token, valid), `PasswordResetIntegrationTest` (2 — Postgres+Mailpit round-trip: forgot →
  token from Mailpit → reset → new password logs in / old rejected / reuse rejected; unknown-email
  generic 202 sends nothing, scoped via Mailpit search). Full backend suite green (168).
