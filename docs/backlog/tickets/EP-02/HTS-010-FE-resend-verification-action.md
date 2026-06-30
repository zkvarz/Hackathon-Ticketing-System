# HTS-010 — [FE] Resend verification action (login + verification screens)

| Field | Value |
|-------|-------|
| **ID** | HTS-010 |
| **Type** | FE |
| **Epic** | EP-02 Authentication |
| **Story** | ST-03 Resend |
| **Status** | TODO |
| **Depends on** | HTS-008, HTS-009 |
| **Blocks** | — |
| **Traceability** | FR-S3, FR-A10; NFR-3; architecture.md §11; wireframe image2 |

## Goal
Expose a "Resend verification email" action on both the login screen ("Account not verified?")
and the verification-result error state, calling the resend endpoint and confirming to the user.

## Scope
- In scope: resend control on `/login` and `/verify` error state; email input (or reuse the
  login email); call `POST /api/auth/resend`; generic success confirmation; throttle the
  button while in flight.
- Out of scope: backend (HTS-009); the verify screen layout itself (HTS-008).

## Technical approach
- Shared `ResendVerification` component reused by both screens.
- Always shows a generic "If that account needs verification, a new email has been sent"
  confirmation (mirrors the no-enumeration backend behavior).

## Acceptance criteria
- [ ] AC-1 — Resend control appears on the login screen and the verification error state.
- [ ] AC-2 — Triggering it calls the resend API and shows a generic confirmation.
- [ ] AC-3 — The button is disabled while the request is in flight (no double submit).
- [ ] AC-4 — A server error shows a non-blocking error, control remains usable.

## Test plan
**Component (Vitest + RTL):**
- Positive: click → API called once → confirmation shown.
- Negative: rapid double-click → only one in-flight request (button disabled).
- Boundary: empty email (on standalone use) → inline validation before calling.

**API-contract (MSW):**
- Mock 202 → confirmation. Mock 500 → error message, control re-enabled.

## How to run / verify
```bash
cd frontend && npm test -- resend
npm run dev   # /login and /verify?token=bad
```

## Definition of Done
- [ ] AC-1..AC-4 met
- [ ] Component + MSW tests pass (positive/negative/boundary)
- [ ] In-flight disabling verified
- [ ] INDEX.md status updated
