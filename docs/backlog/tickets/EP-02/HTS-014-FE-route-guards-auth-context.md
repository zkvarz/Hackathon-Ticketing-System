# HTS-014 — [FE] Route guards + auth context + 401 handling/redirect

| Field | Value |
|-------|-------|
| **ID** | HTS-014 |
| **Type** | FE |
| **Epic** | EP-02 Authentication |
| **Story** | ST-05 Enforcement |
| **Status** | TODO |
| **Depends on** | HTS-012, HTS-013 |
| **Blocks** | HTS-016 |
| **Traceability** | FR-A12; NFR-2, NFR-3; architecture.md §11 |

## Goal
Gate the application routes behind authentication: an auth context backed by `GET /api/auth/me`,
route guards that redirect unauthenticated users to login, and global handling of 401 responses.

## Scope
- In scope: `AuthProvider` context bootstrapping from `/api/auth/me`; `RequireAuth` route
  guard wrapping app routes (`/board`, `/teams`, `/epics`, `/tickets/:id`); redirect to
  `/login` (preserving intended destination); global API-client interceptor that on 401 clears
  auth state and redirects to login; survive page refresh (NFR-2) by re-fetching `me`.
- Out of scope: backend enforcement (HTS-013); individual screens.

## Technical approach
- On app load, query `me`; while pending show a top-level loading state.
- `RequireAuth` renders children only when authenticated, else `<Navigate to="/login">`.
- API client 401 handler centralizes redirect so every feature inherits it.
- Because session lives in an HttpOnly cookie, a refresh re-establishes auth via `me` (NFR-2).

## Acceptance criteria
- [ ] AC-1 — Unauthenticated access to a guarded route redirects to `/login`.
- [ ] AC-2 — Authenticated user reaches guarded routes; refresh keeps them logged in.
- [ ] AC-3 — A 401 from any API call clears auth state and redirects to login.
- [ ] AC-4 — After login, the user lands on their originally requested route (or board).

## Test plan
**Component (Vitest + RTL):**
- Positive: with `me` returning a user, guarded route renders.
- Negative: with `me` 401, guarded route redirects to login.
- Boundary: pending `me` shows loading (not a flash of login then content).

**API-contract (MSW):**
- Mock `me` 200 → access granted. Mock `me` 401 → redirect. Mock a mid-session 401 on a data call → global redirect + state cleared.

## How to run / verify
```bash
cd frontend && npm test -- guard
npm run dev   # try visiting /board while logged out
```

## Definition of Done
- [ ] AC-1..AC-4 met
- [ ] Component + MSW tests pass (positive/negative/boundary)
- [ ] Refresh-survives-login verified (NFR-2)
- [ ] INDEX.md status updated
