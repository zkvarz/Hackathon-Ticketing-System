# HTS-038 — [FE] Password reset flow (frontend)

| Field | Value |
|-------|-------|
| **ID** | HTS-038 |
| **Type** | FE |
| **Epic** | EP-09 Stretch |
| **Story** | ST-01 Password reset |
| **Status** | DONE |
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
- [x] AC-1 — Requesting a reset calls the API and shows a generic confirmation.
- [x] AC-2 — Reset screen submits a valid new password and routes to login on success.
- [x] AC-3 — Mismatched/short password blocked client-side; server token errors surfaced.
- [x] AC-4 — Loading/error states shown.

## Test plan
**Component (Vitest + RTL):** positive (valid request/reset), negative (mismatch, short), boundary (length 7/8).
**API-contract (MSW):** mock forgot 202, reset 200/400 (token invalid).

## How to run / verify
```bash
cd frontend && npm test -- password-reset
```

## Definition of Done
- [x] AC-1..AC-4 met; component + MSW tests pass (positive/negative/boundary)
- [x] INDEX.md status updated

## Implementation notes (as built)
- `api/auth.ts`: `forgotPassword(email)` → `POST /auth/forgot-password`; `resetPassword(token,
  password)` → `POST /auth/reset-password` (throws `ApiError` `TOKEN_INVALID` on 400).
- `ForgotPasswordPage`: email field + client email-format check → generic "Check your email"
  confirmation shown regardless of account existence (no enumeration, mirrors HTS-037); loading +
  generic-error states.
- `ResetPasswordPage`: reads `?token=`; new + confirm password with client validation (length
  8..128, match — server re-validates); on success fires a success toast and `navigate('/login')`;
  a missing token renders an invalid-link state (with a "request a new link" affordance) and no
  form; server `TOKEN_INVALID` surfaced inline; 400 field errors mapped onto the password field.
- Routes `/forgot-password` and `/reset-password` added to `router.tsx` (public, alongside
  `/verify`); a "Forgot password?" link added to `LoginPage`.
- Tests (`password-reset.test.tsx`, 8): Forgot — trimmed-email happy path + generic confirmation,
  invalid-email client block (no API call), 500 generic error/form-usable; Reset — valid submit
  routes to a `/login` stub (classic MemoryRouter/Routes), mismatch blocked, length 7/8 boundary,
  server `TOKEN_INVALID` surfaced (stays on screen), missing-token invalid state. Full FE suite
  green (96); typecheck clean.
