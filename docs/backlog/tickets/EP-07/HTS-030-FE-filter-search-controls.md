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
- [ ] AC-1 — Typing in search narrows the board (case-insensitive substring).
- [ ] AC-2 — Type and epic filters narrow the board; combined filters AND together.
- [ ] AC-3 — Clear resets all filters and restores the full board.
- [ ] AC-4 — A filtered-to-empty result shows an empty state, not an error.

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
- [ ] AC-1..AC-4 met
- [ ] Component + MSW tests pass (positive/negative/boundary)
- [ ] Controls + count match wireframe image1
- [ ] INDEX.md status updated
