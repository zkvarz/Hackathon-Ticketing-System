# HTS-018 — [FE] Epic management screen: team selector, list, create/edit/delete

| Field | Value |
|-------|-------|
| **ID** | HTS-018 |
| **Type** | FE |
| **Epic** | EP-04 Epics |
| **Story** | ST-01 Epic management |
| **Status** | TODO |
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
- [ ] AC-1 — Selecting a team lists its epics with counts; empty state when none.
- [ ] AC-2 — Create adds an epic to the selected team; blank title blocked.
- [ ] AC-3 — Edit updates title/description; the team field is not editable.
- [ ] AC-4 — Delete disabled (with reason) for referenced epics; enabled and confirmed for unreferenced.

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
- [ ] AC-1..AC-4 met
- [ ] Component + MSW tests pass (positive/negative/boundary)
- [ ] Team-fixed-on-create + disabled-delete match wireframe image5
- [ ] INDEX.md status updated
