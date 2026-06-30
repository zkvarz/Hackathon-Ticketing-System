# HTS-038 — [FE] Password reset flow (frontend)

| Field | Value |
|-------|-------|
| **ID** | HTS-038 |
| **Type** | FE |
| **Epic** | EP-09 Stretch |
| **Story** | ST-01 Password reset |
| **Status** | TODO |
| **Priority** | Optional / stretch |
| **Depends on** | HTS-037, HTS-012 |
| **Blocks** | — |
| **Traceability** | Stretch §2.10; NFR-3; architecture.md §11 |

## Goal
Provide "Forgot password?" request and reset screens wired to the backend reset flow.

## Scope
- In scope: forgot-password request form (email) with generic confirmation; reset screen
  reading `?token=` with new + confirm password fields; success → login; loading/error states.
- Out of scope: backend (HTS-037).

## Acceptance criteria
- [ ] AC-1 — Requesting a reset calls the API and shows a generic confirmation.
- [ ] AC-2 — Reset screen submits a valid new password and routes to login on success.
- [ ] AC-3 — Mismatched/short password blocked client-side; server token errors surfaced.
- [ ] AC-4 — Loading/error states shown.

## Test plan
**Component (Vitest + RTL):** positive (valid request/reset), negative (mismatch, short), boundary (length 7/8).
**API-contract (MSW):** mock forgot 202, reset 200/400 (token invalid).

## How to run / verify
```bash
cd frontend && npm test -- password-reset
```

## Definition of Done
- [ ] AC-1..AC-4 met; component + MSW tests pass (positive/negative/boundary)
- [ ] INDEX.md status updated
