# HTS-036 — [QA/E2E] Playwright critical-path suite (should-have)

| Field | Value |
|-------|-------|
| **ID** | HTS-036 |
| **Type** | QA/E2E |
| **Epic** | EP-08 Cross-cutting & Delivery |
| **Story** | ST-05 E2E (should-have) |
| **Status** | IN-REVIEW |
| **Depends on** | HTS-028, HTS-035 |
| **Blocks** | — |
| **Priority** | Should-have (non-blocking) |
| **Traceability** | DoD-1, DoD-3, DoD-6; NFR-6; architecture.md §12 |

## Goal
Provide a small, high-value end-to-end browser suite covering the critical user journeys,
running against the full `docker compose` stack with Mailpit.

## Scope
- In scope: Playwright project + 3-4 specs:
  1. **DoD-1** — sign up → read verification link from Mailpit API → verify → log in.
  2. **DoD-6** — create a ticket, drag it to another column, refresh, confirm it stayed.
  3. **DoD-3** — create, edit, and delete a ticket.
  (optional) team/epic create + delete-blocked-when-referenced.
- Out of scope: exhaustive E2E coverage; running in CI (optional follow-up).

## Technical approach
- Playwright config targets the composed app URL; a setup brings the stack up (or assumes it's up).
- The signup spec polls Mailpit's HTTP API to extract the verification token/link (architecture.md §10).
- Specs are independent and reset state by creating their own data (no shared fixtures relied upon).

## Acceptance criteria
- [x] AC-1 — Signup→verify→login spec passes using Mailpit to obtain the link. **Verified green
  against the live stack.**
- [~] AC-2 — Drag-persists-after-refresh spec authored; needs a freshly built stack to run green
  (see Verification status).
- [~] AC-3 — Ticket CRUD spec authored; needs a freshly built stack to run green.
- [x] AC-4 — Suite runs against `docker compose`; run instructions documented (README + below).

## Test plan
The suite *is* the test. Reliability practices:
- Use role/text selectors and explicit waits (avoid arbitrary sleeps) to limit flake.
- Each spec is self-contained (creates its own team/ticket).

## How to run / verify
```bash
docker compose up --build -d
cd frontend && npx playwright test
```

## Definition of Done
- [x] AC-1 + AC-4 met; AC-2/AC-3 specs authored (see Verification status)
- [ ] **All specs green against the composed stack** — pending a fresh `docker compose up --build`
  (the currently-running containers are a stale build; 2/5 green live, see below)
- [x] Run instructions documented (README §Running the tests)
- [x] INDEX.md status updated (IN-REVIEW)

## Implementation notes (as built)
- **Tooling:** `@playwright/test` (^1.61) + chromium. `frontend/playwright.config.ts`: `testDir
  ./e2e`, baseURL `http://localhost:8081` (override `PW_BASE_URL`), Mailpit at `:8025` (override
  `MAILPIT_URL`). Two projects — a `setup` project (`auth.setup.ts`) registers/verifies/logs in one
  shared user and saves `storageState`; the `e2e` project reuses it (`dependencies: ['setup']`) so
  most specs skip re-verifying. `npm run e2e` / `npm run e2e:report` added.
- **Vitest isolation:** `vite.config.ts` `test.include` scoped to `src/**` so Vitest never collects
  the `*.spec.ts` E2E files; `.gitignore` ignores `frontend/e2e/.auth/` (saved session) + reports.
- **Specs (5):** `auth.spec.ts` (DoD-1, opts out of the shared session to log in from scratch);
  `ticket-crud.spec.ts` (DoD-3); `board-drag.spec.ts` (DoD-6 — real mouse drag that clears dnd-kit's
  5px activation distance, then asserts persistence after `page.reload()`); `teams.spec.ts`
  (optional delete-when-referenced guard). `helpers.ts` centralizes unique data, Mailpit link
  extraction (`expect.poll` over the search + message APIs — no fixed sleeps), and team/ticket
  creation. Selectors are role/label/text based.
- **Verification status:** config validated (`playwright test --list` → 5 tests) and run against the
  live stack: **2/5 green — the `setup` auth bootstrap and `auth.spec` (DoD-1 signup→verify→login
  via Mailpit) pass end-to-end.** The other 3 fail only because the running `hts-frontend`/backend
  containers are an **old image** — the page snapshot shows the pre-HTS-020 placeholder ("This
  screen is implemented in a later ticket.") at `/tickets/new`. They are not spec defects; the app
  code they exercise already has full green unit/integration coverage. Running all 5 green requires
  rebuilding the stack from current source (`docker compose up --build -d`, or the podman build +
  `--no-build` path), which is the operator step this ticket documents — the live stack here is not
  managed by this session's engine, so it was not torn down/rebuilt.
