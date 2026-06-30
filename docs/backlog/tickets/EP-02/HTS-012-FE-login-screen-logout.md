# HTS-012 — [FE] Login screen + logout control (user menu)

| Field | Value |
|-------|-------|
| **ID** | HTS-012 |
| **Type** | FE |
| **Epic** | EP-02 Authentication |
| **Story** | ST-04 Login/logout |
| **Status** | TODO |
| **Depends on** | HTS-003, HTS-011 |
| **Blocks** | HTS-014 |
| **Traceability** | FR-S4, FR-A3; NFR-3; architecture.md §11; wireframes image1, image2 |

## Goal
Provide the login screen and a header user menu with a logout action, wiring both to the auth
API and the app's auth context.

## Scope
- In scope: `/login` form (email/password); submit to login API; on success route to `/board`;
  collapsed user-menu in the header (shows email) with a "Log out" item that calls logout and
  returns to `/login`; surfacing "account not verified" with a resend link (HTS-010); links to sign-up.
- Out of scope: route guards/auth context internals (HTS-014); backend (HTS-011).

## Technical approach
- Login form using shared UX-state primitives; on 403 `EMAIL_NOT_VERIFIED`, show the resend affordance.
- Header user menu per wireframe image1 (collapsed menu includes Log out).
- Logout calls the API and clears client auth state.

## Acceptance criteria
- [ ] AC-1 — Correct credentials log in and route to the board.
- [ ] AC-2 — Wrong credentials show a generic error (no field-specific leak).
- [ ] AC-3 — `EMAIL_NOT_VERIFIED` response surfaces a resend affordance.
- [ ] AC-4 — The user menu shows the logged-in email and a Log out action that logs out and returns to login.

## Test plan
**Component (Vitest + RTL):**
- Positive: valid submit calls login once; user menu renders email after auth.
- Negative: empty fields blocked; generic error rendered on 401.
- Boundary: 403 verification-required path shows resend, not a generic error.

**API-contract (MSW):**
- Mock 200 login → routed to board; `me` reflects the user.
- Mock 401 → generic error. Mock 403 `EMAIL_NOT_VERIFIED` → resend affordance. Logout 204 → returns to login.

## How to run / verify
```bash
cd frontend && npm test -- login
npm run dev   # /login
```

## Definition of Done
- [ ] AC-1..AC-4 met
- [ ] Component + MSW tests pass (positive/negative/boundary)
- [ ] Logout returns to login and clears auth state
- [ ] INDEX.md status updated
