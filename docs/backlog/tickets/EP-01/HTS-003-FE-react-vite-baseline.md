# HTS-003 — [FE] Vite + React + TS baseline: router shell, API client, layout, Dockerfile (nginx)

| Field | Value |
|-------|-------|
| **ID** | HTS-003 |
| **Type** | FE |
| **Epic** | EP-01 Foundation |
| **Story** | ST-03 Frontend baseline |
| **Status** | TODO |
| **Depends on** | HTS-001 |
| **Blocks** | HTS-006, HTS-008, HTS-012, HTS-032, HTS-004 |
| **Traceability** | architecture.md §3, §11, §12; NFR-3, NFR-4 |

## Goal
Create a runnable React + TypeScript SPA shell (routing, typed API client, app layout,
shared UX-state primitives) that builds into an nginx container and can reach the backend
health endpoint — the foundation all frontend features build on.

## Scope
- In scope: Vite + React 18 + TS project; React Router with placeholder routes
  (`/login`, `/signup`, `/verify`, `/board`, `/teams`, `/epics`, `/tickets/:id`); TanStack
  Query provider; typed fetch-based API client (sends credentials + CSRF header, parses the
  standard error model); app layout/header shell; shared `Loading`/`Empty`/`Error` components
  (NFR-3 primitives); Vitest + RTL + MSW test setup; multi-stage Dockerfile (build → nginx)
  with `nginx.conf` doing SPA fallback + `/api` proxy to backend.
- Out of scope: real screens/feature logic (later epics); auth guards (HTS-014); global UX
  polish (HTS-032).

## Technical approach
- Vite scaffold; strict TS config.
- API client base URL configurable; in Compose, nginx proxies `/api` → `backend:8080` so the
  browser sees same-origin (architecture.md §4) — simplifies cookies/CSRF.
- Router renders placeholder pages; a tiny "backend status" widget calls `/api/health` to
  prove connectivity.
- Shared state components used everywhere later (loading/empty/error).

## Acceptance criteria
- [ ] AC-1 — `npm run build` produces a static bundle; `npm run dev` serves the shell locally.
- [ ] AC-2 — SPA loads in the browser via the `frontend` container (nginx) at `http://localhost:8081`.
- [ ] AC-3 — The shell successfully calls `GET /api/health` through the nginx `/api` proxy.
- [ ] AC-4 — Direct navigation to a client route (e.g. `/board`) is served by SPA fallback (no 404).

## Test plan
**Component (Vitest + React Testing Library):**
- Positive: app shell renders header + active route; `Loading`/`Empty`/`Error` components render their states.
- Negative: API client surfaces a parsed error-model object on a 4xx/5xx response.
- Boundary: API client handles an empty/204 response and a malformed JSON body without crashing.

**API-contract (MSW):**
- Mock `GET /api/health` success → status widget shows UP.
- Mock `GET /api/health` failure → shell shows the error state, not a blank screen.

## How to run / verify
```bash
cd frontend
npm install
npm test                              # vitest component + MSW tests
npm run build
docker compose up --build frontend backend   # SPA at :8081 reaches backend health
```

## Definition of Done
- [ ] AC-1..AC-4 met
- [ ] Component tests (positive/negative/boundary) pass
- [ ] MSW API-contract tests pass
- [ ] Container builds; SPA served by nginx reaches backend through `/api` proxy
- [ ] INDEX.md status updated
