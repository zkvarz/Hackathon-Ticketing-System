# HTS-030 — [FE] Filter/search controls + wiring + clear

| Field | Value |
|-------|-------|
| **ID** | HTS-030 |
| **Type** | FE |
| **Epic** | EP-07 Kanban Board |
| **Story** | ST-03 Filters & search |
| **Status** | TODO |
| **Depends on** | HTS-026, HTS-029 |
| **Blocks** | — |
| **Traceability** | FR-B9; NFR-3; architecture.md §11; wireframe image1 |

## Goal
Add the board's filter/search controls — title search, type filter, epic filter, and a clear
action — wired to the server-side filtered query, combined with AND.

## Scope
- In scope: search input (title), type dropdown, epic dropdown (for the selected team), Clear
  button, and a result count; debounced search; controls drive the `?type=&epicId=&q=` query;
  loading/empty states for filtered results.
- Out of scope: backend (HTS-029).

## Technical approach
- Controls per wireframe image1 (Search / Type / Epic / Clear / count).
- Debounce `q`; combine all active filters into the query (AND, server-side).
- Epic options scoped to the selected team; Clear resets all filters.

## Acceptance criteria
- [x] AC-1 — Typing in search narrows the board (case-insensitive substring).
- [x] AC-2 — Type and epic filters narrow the board; combined filters AND together.
- [x] AC-3 — Clear resets all filters and restores the full board.
- [x] AC-4 — A filtered-to-empty result shows an empty state, not an error.

## Test plan
**Component (Vitest + RTL):**
- Positive: setting each control issues the right query params; count updates.
- Negative: no matches → empty state.
- Boundary: clearing restores; rapid typing debounces to a single query; switching team resets epic filter.

**API-contract (MSW):**
- Mock filtered responses for given params; assert the request carried the expected query string.

## How to run / verify
```bash
cd frontend && npm test -- filters
npm run dev   # /board, use Search/Type/Epic/Clear
```

## Definition of Done
- [x] AC-1..AC-4 met
- [x] Component + MSW tests pass (positive/negative/boundary) — `filters.test.tsx` (6); existing
  `BoardPage.test.tsx` still green
- [x] Controls + count match wireframe image1 (Search / Type / Epic / Clear + result count)
- [x] INDEX.md status updated

## Implementation notes (as built)
- Extended `BoardPage` with a `role="search"` toolbar: debounced title search (250ms), type and
  epic selects, a Clear button (disabled when no filter is active), and a live result count.
- All active filters combine into the server query key `['tickets', teamId, type, epic, q]` →
  `listTickets(teamId, {type, epicId, q})` (server-side AND, HTS-029). Epic options are scoped to
  the selected team; switching team resets the epic filter.
- Filtered-to-empty renders the shared `Empty` state (with a Clear action), never an error.
- Test infra: added a default empty `/api/epics` MSW handler (board now loads the team's epics).
