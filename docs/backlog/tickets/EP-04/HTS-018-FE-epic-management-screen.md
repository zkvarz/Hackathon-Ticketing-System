# HTS-018 — [FE] Epic management screen: team selector, list, create/edit/delete

| Field | Value |
|-------|-------|
| **ID** | HTS-018 |
| **Type** | FE |
| **Epic** | EP-04 Epics |
| **Story** | ST-01 Epic management |
| **Status** | DONE |
| **Depends on** | HTS-016, HTS-017 |
| **Blocks** | HTS-020 |
| **Traceability** | FR-S8, FR-E3, FR-E8; NFR-3; architecture.md §11; wireframe image5 |

## Goal
Provide the epic management screen: a team selector, a list of that team's epics (title, ticket
count, modified), and create/edit/delete with delete disabled for referenced epics.

## Scope
- In scope: `/epics` route; team selector; epic list with counts; create (team fixed to the
  selected team); edit title/description; delete with confirmation, disabled when referenced;
  loading/empty/error states.
- Out of scope: backend (HTS-017); ticket epic-dropdown linkage (HTS-022).

## Technical approach
- Team selector drives the list query (`?teamId=`).
- Create form fixes the team to the current selection (team immutable, FR-E2).
- Disable delete when ticket count > 0; defensively handle 409 (race).
- Matches wireframe image5 (Edit epic panel; disabled delete when referenced).

## Acceptance criteria
- [x] AC-1 — Selecting a team lists its epics with counts; empty state when none.
- [x] AC-2 — Create adds an epic to the selected team; blank title blocked.
- [x] AC-3 — Edit updates title/description; the team field is not editable.
- [x] AC-4 — Delete disabled (with reason) for referenced epics; enabled and confirmed for unreferenced.

## Test plan
**Component (Vitest + RTL):**
- Positive: list per team; create/edit/delete-unreferenced update the UI.
- Negative: blank title blocked; delete disabled when count > 0.
- Boundary: switching teams refetches the correct list; epic with 0 tickets deletable, 1 not.

**API-contract (MSW):**
- Mock list per teamId; create 201/400; delete 409 `EPIC_HAS_TICKETS` → validation message.

## How to run / verify
```bash
cd frontend && npm test -- epics
npm run dev   # /epics
```

## Definition of Done
- [x] AC-1..AC-4 met
- [x] Component + MSW tests pass (positive/negative/boundary)
- [x] Team-fixed-on-create + disabled-delete match wireframe image5
- [x] INDEX.md status updated

## Implementation notes
- `features/epics/EpicsPage` wired into `/epics` (replaces placeholder). A team `<select>`
  (from `listTeams`, defaults to the first team) drives the `['epics', teamId]` query;
  switching teams refetches. Loading/Empty/Error states per NFR-3.
- Create fixes the team to the current selection (no team field); edit is inline title +
  description only (team not editable, FR-E2); delete disabled when `ticketCount > 0` (reason
  via `title`) with a two-step inline confirm. 409 `EPIC_HAS_TICKETS` mapped to a readable
  message defensively.
- `api/epics.ts` adds `listEpics`/`createEpic`/`updateEpic`/`deleteEpic`.
- Tests (6): list+counts, empty, blank-title blocked, create→appears, delete
  enabled/disabled boundary, team-switch refetch.
