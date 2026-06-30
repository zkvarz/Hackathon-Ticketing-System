# HTS-006 — [FE] Sign-up screen: form, validation, success/error states

| Field | Value |
|-------|-------|
| **ID** | HTS-006 |
| **Type** | FE |
| **Epic** | EP-02 Authentication |
| **Story** | ST-01 Sign up |
| **Status** | DONE |
| **Depends on** | HTS-003, HTS-005 |
| **Blocks** | — |
| **Traceability** | FR-S1; FR-A1, FR-A4; NFR-3; architecture.md §11; wireframe image2 |

## Goal
Provide the sign-up screen: email, password, and confirm-password fields with client-side
validation, calling the signup API and showing success and error states.

## Scope
- In scope: `/signup` route + form; client validation (email format, password ≥8, confirm
  matches); submit to `POST /api/auth/signup`; success state directing the user to check their
  email; mapping of 400/409 responses to inline/field errors; link to login.
- Out of scope: the actual verification screen (HTS-008); backend logic (HTS-005).

## Technical approach
- Form component using the shared UX-state primitives (loading/error) from HTS-003.
- Client validation mirrors server limits (8..128) but the **server remains authoritative** (FR-K8 spirit).
- On 409 `EMAIL_TAKEN`, show a field-level "email already registered" message.
- On success, show "verification email sent — check your inbox" (no auto-login, per FR-A9 flow).

## Acceptance criteria
- [x] AC-1 — Valid input submits and shows the success/check-email state.
- [x] AC-2 — Mismatched confirm password blocks submit with an inline error.
- [x] AC-3 — Password <8 chars blocks submit with an inline error.
- [x] AC-4 — Server 409 (email taken) and 400 (validation) render as readable errors, not a crash.

## Test plan
**Component (Vitest + RTL):**
- Positive: filling valid fields enables submit; submitting calls the API client once with normalized payload.
- Negative: mismatched confirm / invalid email / empty fields show errors and block submit.
- Boundary: password length 7 (blocked) vs 8 (allowed).

**API-contract (MSW):**
- Mock 201 → success state shown.
- Mock 409 `EMAIL_TAKEN` → email field error shown.
- Mock 400 with field errors → mapped to the right fields.
- Mock 500 → generic error state, form still usable.

## How to run / verify
```bash
cd frontend && npm test -- signup
npm run dev   # visit /signup
```

## Definition of Done
- [x] AC-1..AC-4 met
- [x] Component tests (positive/negative/boundary) pass
- [x] MSW tests cover 201/409/400/500
- [x] Loading/error/success states present (NFR-3)
- [x] INDEX.md status updated

## Implementation notes
- `features/auth/SignupPage.tsx` wired into the router (replaces the `/signup` placeholder).
- Client validation (UX only; server authoritative): email format, password 8..128, confirm
  match. Submit sends a trimmed email; server normalizes further.
- Server-error mapping: 409 `EMAIL_TAKEN` → email field error; 400 `VALIDATION_FAILED`
  field errors → mapped onto matching inputs; 500/other → generic form error, form stays
  usable (submit re-enabled); success → check-email state with a link back to login (no
  auto-login, FR-A9).
- `api/auth.ts` adds typed `signup()` (and `verifyEmail()` ahead of HTS-008).
- Tests (7): positive (trimmed payload sent once → success), negative (confirm mismatch /
  invalid email block submit, API not called), boundary (7 vs 8 char password), MSW
  201/409/400/500.
