# HTS-036 — [QA/E2E] Playwright critical-path suite (should-have)

| Field | Value |
|-------|-------|
| **ID** | HTS-036 |
| **Type** | QA/E2E |
| **Epic** | EP-08 Cross-cutting & Delivery |
| **Story** | ST-05 E2E (should-have) |
| **Status** | TODO |
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
- [ ] AC-1 — Signup→verify→login spec passes using Mailpit to obtain the link.
- [ ] AC-2 — Drag-persists-after-refresh spec passes.
- [ ] AC-3 — Ticket CRUD spec passes.
- [ ] AC-4 — Suite runs against `docker compose` and documents how to run it.

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
- [ ] AC-1..AC-4 met
- [ ] All specs green against the composed stack
- [ ] Run instructions documented (README/HTS-034)
- [ ] INDEX.md status updated
