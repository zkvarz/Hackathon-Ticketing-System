# HTS-022 — [FE] Team/epic dropdown linkage (team change clears/replaces epic)

| Field | Value |
|-------|-------|
| **ID** | HTS-022 |
| **Type** | FE |
| **Epic** | EP-05 Tickets |
| **Story** | ST-02 References & team-change |
| **Status** | DONE |
| **Depends on** | HTS-020, HTS-021 |
| **Blocks** | — |
| **Traceability** | FR-K5, FR-E6; NFR-3; architecture.md §11 |

## Goal
In the ticket form, make the epic dropdown depend on the selected team: it lists only that
team's epics, and changing the team clears or replaces the previously selected epic.

## Scope
- In scope: epic dropdown populated from the selected team's epics (`?teamId=`); on team change,
  clear the selected epic (and refetch the new team's epics); defensive handling of a backend
  `EPIC_TEAM_MISMATCH` (shouldn't occur if UI is correct).
- Out of scope: backend rule (HTS-021).

## Technical approach
- Epic options query keyed by selected team; team change resets the epic field to empty (FR-K5).
- Epic is optional (FR-E6) — empty selection allowed.

## Acceptance criteria
- [x] AC-1 — Epic dropdown shows only epics for the currently selected team.
- [x] AC-2 — Changing the team clears the selected epic and reloads epic options.
- [x] AC-3 — Submitting with no epic is allowed.
- [x] AC-4 — A backend `EPIC_TEAM_MISMATCH` (edge/race) surfaces as a clear field error, not a crash.

## Test plan
**Component (Vitest + RTL):**
- Positive: selecting team A shows A's epics; choosing one then submitting sends matching teamId/epicId.
- Negative: changing team after picking an epic clears the epic.
- Boundary: team with no epics shows an empty/disabled epic dropdown; switching back restores options.

**API-contract (MSW):**
- Mock epics per teamId; mock submit success and `EPIC_TEAM_MISMATCH` 400 → field error.

## How to run / verify
```bash
cd frontend && npm test -- TicketEpicLinkage
npm run dev   # open the ticket form, switch teams
```

## Definition of Done
- [x] AC-1..AC-4 met
- [x] Component + MSW tests pass (positive/negative/boundary)
- [x] Team-change-clears-epic behavior verified
- [x] INDEX.md status updated
