# HTS-031 — [BE] Standardized error response model + global exception handling + status codes

| Field | Value |
|-------|-------|
| **ID** | HTS-031 |
| **Type** | BE |
| **Epic** | EP-08 Cross-cutting & Delivery |
| **Story** | ST-01 Error model & UX states |
| **Status** | TODO |
| **Depends on** | HTS-002 |
| **Blocks** | HTS-013, HTS-032 (contract) |
| **Traceability** | FR-P4; NFR-1; architecture.md §8 |

## Goal
Define a single error response model and global exception handling so every 4xx/5xx returns a
consistent, meaningful payload with the correct HTTP status — the contract the FE relies on.

## Scope
- In scope: error DTO (timestamp, status, error, `code`, message, `fieldErrors[]`); a
  `@RestControllerAdvice` mapping validation, not-found, conflict, auth, and generic errors to
  the right status codes (architecture.md §8); stable `code` values used across the app.
- Out of scope: per-feature business rules (their tickets) — they throw typed exceptions this
  advice maps.

## Technical approach
- Central exception types (NotFound, Conflict, Validation, Forbidden) → mapped to 404/409/400/403.
- Bean-validation failures → 400 with `fieldErrors`.
- Catch-all → 500 with a safe generic message (no stack/secret leakage, NFR-1).
- Document the `code` catalog (e.g. `EMAIL_TAKEN`, `TEAM_HAS_CHILDREN`, `EPIC_TEAM_MISMATCH`, `TOKEN_INVALID`).

## Acceptance criteria
- [ ] AC-1 — Validation failure returns 400 with per-field errors.
- [ ] AC-2 — Not-found returns 404; conflict returns 409; forbidden/CSRF 403; unauthenticated 401.
- [ ] AC-3 — All responses share the same JSON shape with a stable `code`.
- [ ] AC-4 — Unexpected exceptions return 500 with no stack trace or secret in the body.

## Test plan
**Unit (JUnit 5 + Mockito / Spring test):**
- Positive: each exception type maps to the expected status + code.
- Negative: an unmapped runtime exception → 500 generic (no leakage).
- Boundary: validation with multiple field errors lists all; empty/zero field-errors omitted cleanly.

**Integration (Testcontainers — Postgres):**
- Trigger a real 409 (duplicate team name) and a real 404 (missing ticket) through the API and assert the body shape + code.

## How to run / verify
```bash
cd backend && ./mvnw test -Dtest='*ErrorHandling*'
```

## Definition of Done
- [ ] AC-1..AC-4 met
- [ ] Unit + integration tests pass (positive/negative/boundary)
- [ ] `code` catalog documented (and referenced by FE HTS-032)
- [ ] No sensitive data in error bodies
- [ ] INDEX.md status updated
