# HTS-009 — [BE] Resend verification endpoint (invalidate prior unused tokens)

| Field | Value |
|-------|-------|
| **ID** | HTS-009 |
| **Type** | BE |
| **Epic** | EP-02 Authentication |
| **Story** | ST-03 Resend |
| **Status** | TODO |
| **Depends on** | HTS-007 |
| **Blocks** | HTS-010 |
| **Traceability** | FR-A10, FR-A11; architecture.md §9, §10 |

## Goal
Let an unverified user request a fresh verification email; issuing the new token invalidates
any earlier unused tokens for that user.

## Scope
- In scope: `POST /api/auth/resend` (public); look up unverified user by email; invalidate
  prior unused tokens; issue + send a new token (reuse HTS-007 send logic).
- Out of scope: verify endpoint (HTS-007); FE action (HTS-010).

## Technical approach
- Accept email; normalize (lower/trim). If a matching **unverified** user exists, invalidate
  their outstanding unused tokens (mark consumed/invalidated) and issue a new one (FR-A11).
- **Privacy:** respond with a generic 200/202 regardless of whether the email exists or is
  already verified, to avoid account enumeration (defense-in-depth; note in architecture.md §9).

## Acceptance criteria
- [ ] AC-1 — Resend for an unverified user issues a new token and invalidates prior unused ones.
- [ ] AC-2 — Only the newest token can verify; older tokens now fail as invalid.
- [ ] AC-3 — Resend for a non-existent/already-verified email returns the same generic success (no enumeration).
- [ ] AC-4 — A new email is sent (captured by Mailpit).

## Test plan
**Unit (JUnit 5 + Mockito):**
- Positive: unverified user → old tokens invalidated, new token saved, mail sent.
- Negative: already-verified user → no new token issued, generic success returned.
- Boundary: user with zero prior tokens → still issues exactly one; user with multiple unused tokens → all invalidated.

**Integration (Testcontainers — Postgres + Mailpit):**
- Issue token A, resend → token B; verifying with A fails, with B succeeds.
- Mailpit shows a second message after resend.

## How to run / verify
```bash
cd backend && ./mvnw test -Dtest='*Resend*'
curl -X POST localhost:8080/api/auth/resend -H 'Content-Type: application/json' -d '{"email":"a@b.com"}'
```

## Definition of Done
- [ ] AC-1..AC-4 met
- [ ] Unit tests (positive/negative/boundary) pass
- [ ] Testcontainers integration (token rotation + Mailpit) passes
- [ ] No account-enumeration signal in responses
- [ ] INDEX.md status updated
