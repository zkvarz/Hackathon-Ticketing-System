# HTS-010 — [FE] Resend verification action (login + verification screens)

| Field | Value |
|-------|-------|
| **ID** | HTS-010 |
| **Type** | FE |
| **Epic** | EP-02 Authentication |
| **Story** | ST-03 Resend |
| **Status** | DONE |
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
- [x] AC-1 — Resend control appears on the login screen and the verification error state.
  *(Verification error state wired here; login-screen placement delivered in the next commit,
  HTS-012, where the login screen is built — the same `ResendVerification` component is embedded.)*
- [x] AC-2 — Triggering it calls the resend API and shows a generic confirmation.
- [x] AC-3 — The button is disabled while the request is in flight (no double submit).
- [x] AC-4 — A server error shows a non-blocking error, control remains usable.

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
- [x] AC-1..AC-4 met (login-screen placement embedded in HTS-012)
- [x] Component + MSW tests pass (positive/negative/boundary)
- [x] In-flight disabling verified
- [x] INDEX.md status updated

## Implementation notes
- `features/auth/ResendVerification.tsx`: reusable control. With an `email` prop (login case)
  it shows just the button; standalone (verify case) it renders its own validated email input.
  Confirmation is always generic ("If that account needs verification, a new email has been
  sent") to mirror the backend's no-enumeration behavior. Button disabled while in flight.
- Wired into the VerifyPage invalid/expired state (replacing the disabled placeholder). The
  login screen embeds the same component on the `EMAIL_NOT_VERIFIED` path in HTS-012.
- `api/auth.ts` adds `resendVerification(email)`.
- Tests (4): positive (one call + confirmation), negative (double-click → one request via
  in-flight disable), boundary (standalone empty/invalid email blocks call), MSW 500 →
  non-blocking error, control stays enabled.
