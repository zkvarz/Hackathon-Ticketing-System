# HTS-014 — [FE] Route guards + auth context + 401 handling/redirect

| Field | Value |
|-------|-------|
| **ID** | HTS-014 |
| **Type** | FE |
| **Epic** | EP-02 Authentication |
| **Story** | ST-05 Enforcement |
| **Status** | DONE |
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
- [x] AC-1 — Unauthenticated access to a guarded route redirects to `/login`.
- [x] AC-2 — Authenticated user reaches guarded routes; refresh keeps them logged in.
- [x] AC-3 — A 401 from any API call clears auth state and redirects to login.
- [x] AC-4 — After login, the user lands on their originally requested route (or board).

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
- [x] AC-1..AC-4 met
- [x] Component + MSW tests pass (positive/negative/boundary)
- [x] Refresh-survives-login verified (NFR-2)
- [x] INDEX.md status updated

## Implementation notes
- `AuthProvider` (from HTS-012) already bootstraps from `GET /api/auth/me` — refresh re-fetches
  it, so auth survives reload (NFR-2). This ticket adds the guard + global 401 handling.
- `auth/RequireAuth`: while `me` is in flight → top-level `Loading` (no login flash); when
  anonymous → `<Navigate to="/login" state={{from}}>`; else `<Outlet/>`. Wired in `router.tsx`
  wrapping the `AppLayout` group.
- Global 401: `api/client` exposes `setUnauthorizedHandler`; `AuthProvider` registers one that
  clears the cached `me` on any 401, so `RequireAuth` then redirects — no per-call handling.
- `LoginPage` now returns the user to the intended route (`location.state.from`, default
  `/board`) after login (AC-4).
- Tests: `RequireAuth.test` (authenticated render, anonymous→login, loading→route boundary,
  mid-session 401→login); `App.test` updated to authenticate for the now-guarded routes.
