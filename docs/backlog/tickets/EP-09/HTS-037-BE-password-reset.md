# HTS-037 — [BE] Password reset flow (backend)

| Field | Value |
|-------|-------|
| **ID** | HTS-037 |
| **Type** | BE |
| **Epic** | EP-09 Stretch |
| **Story** | ST-01 Password reset |
| **Status** | TODO |
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
- [ ] AC-1 — Forgot-password issues a token and sends an email (Mailpit) for an existing user.
- [ ] AC-2 — Reset with a valid token sets a new hash; old password no longer works, new one does.
- [ ] AC-3 — Expired/consumed/unknown reset token → rejected.
- [ ] AC-4 — Forgot-password for unknown email returns the same generic success (no enumeration).

## Test plan
**Unit (JUnit + Mockito):** positive (valid reset), negative (expired/consumed/unknown token), boundary (new password length 7/8/128/129; token at expiry edge with fixed clock).
**Integration (Testcontainers — Postgres + Mailpit):** forgot → read token from Mailpit → reset → login with new password succeeds, old fails; reused token rejected.

## How to run / verify
```bash
cd backend && ./mvnw test -Dtest='*PasswordReset*'
```

## Definition of Done
- [ ] AC-1..AC-4 met; unit + integration tests pass (positive/negative/boundary)
- [ ] No enumeration; INDEX.md status updated
