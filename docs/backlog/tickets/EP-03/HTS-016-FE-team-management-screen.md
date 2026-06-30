# HTS-016 — [FE] Team management screen: list, create, rename, delete (disabled when referenced)

| Field | Value |
|-------|-------|
| **ID** | HTS-016 |
| **Type** | FE |
| **Epic** | EP-03 Teams |
| **Story** | ST-01 Team management |
| **Status** | TODO |
| **Depends on** | HTS-014, HTS-015 |
| **Blocks** | HTS-018 |
| **Traceability** | FR-S7, FR-T2, FR-T5; NFR-3; architecture.md §11; wireframe image4 |

## Goal
Provide the team management screen: a table of teams (name, ticket/epic counts, modified), with
create, rename, and delete — delete disabled for teams that still contain tickets or epics.

## Scope
- In scope: `/teams` route; list with counts; create form; inline rename; delete with
  confirmation; delete control disabled (with explanation) when the team has children; loading/
  empty/error states.
- Out of scope: backend (HTS-015); epic screen (HTS-018).

## Technical approach
- TanStack Query for list + mutations; optimistic or refetch-on-success.
- Disable delete when counts > 0 (mirrors backend 409); still handle a 409 defensively (race) by showing the validation message.
- Matches wireframe image4 (disabled Delete for referenced teams).

## Acceptance criteria
- [ ] AC-1 — Teams list renders with name, counts, and modified; empty state shown when none.
- [ ] AC-2 — Create adds a team and it appears in the list; duplicate-name 409 shows a clear message.
- [ ] AC-3 — Rename updates the row; validation errors shown inline.
- [ ] AC-4 — Delete is disabled (with reason) for teams with children; enabled for empty teams and confirms before deleting.

## Test plan
**Component (Vitest + RTL):**
- Positive: list renders; create/rename/delete-empty flows update the UI.
- Negative: duplicate-name error rendered; delete blocked when counts > 0.
- Boundary: team with exactly 0 children is deletable; with 1 child is not.

**API-contract (MSW):**
- Mock list (with and without teams) → table vs empty state.
- Mock create 409 → message. Mock delete 409 `TEAM_HAS_CHILDREN` → validation message.

## How to run / verify
```bash
cd frontend && npm test -- teams
npm run dev   # /teams
```

## Definition of Done
- [ ] AC-1..AC-4 met
- [ ] Component + MSW tests pass (positive/negative/boundary)
- [ ] Disabled-delete behavior matches wireframe image4
- [ ] INDEX.md status updated
