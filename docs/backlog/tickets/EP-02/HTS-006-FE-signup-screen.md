# HTS-006 — [FE] Sign-up screen: form, validation, success/error states

| Field | Value |
|-------|-------|
| **ID** | HTS-006 |
| **Type** | FE |
| **Epic** | EP-02 Authentication |
| **Story** | ST-01 Sign up |
| **Status** | TODO |
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
- [ ] AC-1 — Valid input submits and shows the success/check-email state.
- [ ] AC-2 — Mismatched confirm password blocks submit with an inline error.
- [ ] AC-3 — Password <8 chars blocks submit with an inline error.
- [ ] AC-4 — Server 409 (email taken) and 400 (validation) render as readable errors, not a crash.

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
- [ ] AC-1..AC-4 met
- [ ] Component tests (positive/negative/boundary) pass
- [ ] MSW tests cover 201/409/400/500
- [ ] Loading/error/success states present (NFR-3)
- [ ] INDEX.md status updated
