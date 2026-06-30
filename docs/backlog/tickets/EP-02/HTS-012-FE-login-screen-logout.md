# HTS-012 — [FE] Login screen + logout control (user menu)

| Field | Value |
|-------|-------|
| **ID** | HTS-012 |
| **Type** | FE |
| **Epic** | EP-02 Authentication |
| **Story** | ST-04 Login/logout |
| **Status** | DONE |
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
- [x] AC-1 — Correct credentials log in and route to the board.
- [x] AC-2 — Wrong credentials show a generic error (no field-specific leak).
- [x] AC-3 — `EMAIL_NOT_VERIFIED` response surfaces a resend affordance.
- [x] AC-4 — The user menu shows the logged-in email and a Log out action that logs out and returns to login.

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
- [x] AC-1..AC-4 met
- [x] Component + MSW tests pass (positive/negative/boundary)
- [x] Logout returns to login and clears auth state
- [x] INDEX.md status updated

## Implementation notes
- Introduced `auth/AuthContext` (`AuthProvider` + `useAuth`): bootstraps the current user from
  `GET /api/auth/me` via TanStack Query (refresh-survives, NFR-2), exposes `login`/`logout`
  that update the cached `['auth','me']`. `router.tsx` now has a pathless root wrapping all
  routes in `AuthProvider` (so tests mounting `routes` get it too). HTS-014 adds RequireAuth +
  global 401 handling on top.
- `features/auth/LoginPage`: validates non-empty fields, logs in via the context, routes to
  `/board`; 401 → generic "Invalid email or password"; 403 `EMAIL_NOT_VERIFIED` → embeds the
  shared `ResendVerification` (completing HTS-010's login-screen placement). `api/auth.ts`
  adds `login`/`logout`/`getCurrentUser`.
- `AppLayout` header shows the signed-in email + a Log out button (calls `logout()` →
  `/login`). Default MSW `GET /api/auth/me` → 401 added so the root provider is satisfied.
- Tests (5): login→board (+ header email), empty-fields blocked, 401 generic error, 403
  resend affordance, header logout→login. Use classic `MemoryRouter`/`Routes` to avoid a
  jsdom+MSW AbortSignal clash in the data router on programmatic navigation (browser is fine).
