# HTS-007 — [BE] Email verification: token issue + SMTP send + verify endpoint (24h, single-use)

| Field | Value |
|-------|-------|
| **ID** | HTS-007 |
| **Type** | BE |
| **Epic** | EP-02 Authentication |
| **Story** | ST-02 Email verification |
| **Status** | DONE |
| **Depends on** | HTS-005 |
| **Blocks** | HTS-008, HTS-009, HTS-011 |
| **Traceability** | FR-A6, FR-A7, FR-A8, FR-A9; DoD-1; architecture.md §5, §9, §10 |

## Goal
On sign-up, issue a single-use verification token, send a verification email through the
configurable SMTP service (Mailpit in dev), and provide an endpoint that verifies the token,
marks the user verified, and consumes the token.

## Scope
- In scope: `EmailVerificationToken` entity + repo + migration; token issuance on signup
  (wire into HTS-005's flow); `JavaMailSender` send to `SMTP_HOST`; verification link
  `${APP_BASE_URL}/verify?token=…`; `GET /api/auth/verify?token=…`; 24h TTL + single-use
  enforcement.
- Out of scope: resend (HTS-009); login (HTS-011); the FE result screen (HTS-008).

## Technical approach
- Token entity per architecture.md §6 (user_id, token, expires_at, consumed_at).
- Generate a high-entropy opaque token; store it; email contains the link (token in URL is
  allowed — FR-P6).
- `expires_at = now + APP_VERIFICATION_TOKEN_TTL` (24h, FR-A8).
- Verify: token must exist, not be expired, not be consumed → set `email_verified=true`,
  set `consumed_at`. Success does **not** auto-login (FR-A9); FE routes to login.
- Expired/consumed/unknown token → 400/410 with standard error model (`code=TOKEN_INVALID`).

## Acceptance criteria
- [x] AC-1 — Sign-up triggers a verification email captured by Mailpit, containing a valid link.
- [x] AC-2 — Verifying a fresh token marks the user verified and consumes the token.
- [x] AC-3 — A consumed token cannot be reused (single-use).
- [x] AC-4 — A token older than 24h is rejected as expired.
- [x] AC-5 — Verification does not create a session (no auto-login).

## Test plan
**Unit (JUnit 5 + Mockito):**
- Positive: valid token → user marked verified, token consumed.
- Negative: unknown token → invalid; already-consumed token → invalid.
- Boundary: token at expiry edge — just-before-expiry accepted, just-after-expiry rejected (inject a fixed clock).

**Integration (Testcontainers — Postgres + Mailpit):**
- Sign up → assert Mailpit received one message (via Mailpit HTTP API); extract token from the link.
- `GET /api/auth/verify?token=…` → user `email_verified=true`, `consumed_at` set; second call returns invalid.
- Persisted token expiry equals issue time + 24h.

## How to run / verify
```bash
cd backend && ./mvnw test -Dtest='*Verification*'
docker compose up --build backend db mailpit
# sign up, then open Mailpit UI at http://localhost:8025 to read the link
```

## Definition of Done
- [x] AC-1..AC-5 met
- [x] Unit tests (positive/negative/boundary incl. clock-based expiry) pass
- [x] Testcontainers integration (Postgres + Mailpit) passes end-to-end
- [x] SMTP target is env-configurable (Mailpit ↔ relay1.dataart.com, no code change)
- [x] INDEX.md status updated

## Implementation notes
- `EmailVerificationToken` entity + `V3__email_verification_tokens.sql` (FK cascade on user
  delete, unique token, user index for FR-A11 resend).
- `EmailVerificationService`: `issueAndSend` (32-byte URL-safe token, expiry = now+TTL from
  injected `Clock`, link `${app.base-url}/verify?token=…`, `SimpleMailMessage` via
  `JavaMailSender`); `verify` (unknown/consumed/expired → `TokenInvalidException`; else mark
  user verified + set `consumed_at`). Expiry is exclusive (`now < expiresAt`).
- Email send is **best-effort**: a `MailException` is caught/logged so signup still succeeds
  (the token persists and the user can resend, FR-A10) instead of 500-ing if SMTP is down.
- Wired into `AuthService.signup` (issue+send after persist). `GET /api/auth/verify?token=`
  added to `AuthController`; no session created (FR-A9). `TOKEN_INVALID` (400) added to the
  exception handler. Injectable UTC `Clock` bean (`config/TimeConfig`).
- Tests (28 total green): `EmailVerificationServiceTest` (Mockito + fixed clock: issue+send,
  mail-failure swallowed, verify success, unknown/consumed rejected, expiry boundary
  before/at), `EmailVerificationIntegrationTest` (Postgres + **Mailpit** container: signup →
  1 captured email → extract token → verify 200 + no Set-Cookie → user verified + token
  consumed → expiry≈24h → reuse 400). Updated `SignUpServiceTest` for the new dependency.
- SMTP target is env-only (`SMTP_HOST/PORT` → Mailpit or relay1.dataart.com); no code change.
