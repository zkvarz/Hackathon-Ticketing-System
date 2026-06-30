# HTS-008 — [FE] Email verification result screen (success / expired-invalid)

| Field | Value |
|-------|-------|
| **ID** | HTS-008 |
| **Type** | FE |
| **Epic** | EP-02 Authentication |
| **Story** | ST-02 Email verification |
| **Status** | DONE |
| **Depends on** | HTS-003, HTS-007 |
| **Blocks** | HTS-010 |
| **Traceability** | FR-S2, FR-A9; NFR-3; architecture.md §11; wireframe image2 |

## Goal
Provide the `/verify` screen that reads the token from the URL, calls the verify endpoint, and
shows a success state (with a "Continue to login" action) or an expired/invalid state (with a
resend action placeholder wired in HTS-010).

## Scope
- In scope: `/verify` route reading `?token=`; call `GET /api/auth/verify`; success UI →
  "Continue to login" (no auto-login, FR-A9); error UI for expired/invalid; loading state.
- Out of scope: the resend action itself (HTS-010); backend logic (HTS-007).

## Technical approach
- On mount, parse `token` from query; if missing, show the invalid state immediately.
- Call verify; render success vs. error per the response (standard error model `TOKEN_INVALID`).
- Success routes to `/login`.

## Acceptance criteria
- [x] AC-1 — A valid token shows the success state and a working "Continue to login" link.
- [x] AC-2 — An expired/invalid token shows the error state with a resend affordance.
- [x] AC-3 — A missing token query param shows the invalid state without calling the API.
- [x] AC-4 — A loading state is shown while the verify call is in flight.

## Test plan
**Component (Vitest + RTL):**
- Positive: success response → success UI + login link.
- Negative: error response → error UI; missing token → invalid UI, API not called.
- Boundary: slow response shows loading then resolves to the correct terminal state.

**API-contract (MSW):**
- Mock 200 verify → success.
- Mock 400/410 `TOKEN_INVALID` → error state with resend affordance.

## How to run / verify
```bash
cd frontend && npm test -- verify
npm run dev   # visit /verify?token=fake
```

## Definition of Done
- [x] AC-1..AC-4 met
- [x] Component + MSW tests pass (positive/negative/boundary)
- [x] Loading/success/error states present (NFR-3)
- [x] INDEX.md status updated

## Implementation notes
- `features/auth/VerifyPage.tsx` wired into the router (replaces the `/verify` placeholder).
- Reads `?token=` via `useSearchParams`; a TanStack Query (`enabled: !!token`, `retry:false`)
  calls `GET /api/auth/verify` so a missing token never hits the API (AC-3).
- States: loading (`Verifying your email…`) → success (`Continue to login` → `/login`, no
  auto-login) | invalid-or-expired (message + disabled "Resend verification email" affordance
  wired in HTS-010 + back-to-login link).
- Tests (4, Vitest+RTL+MSW): success+login link, 400 TOKEN_INVALID → invalid state with resend
  affordance, missing token → invalid state with API not called, loading→success boundary.
