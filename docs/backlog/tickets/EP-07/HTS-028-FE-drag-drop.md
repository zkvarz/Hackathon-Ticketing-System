# HTS-028 — [FE] Drag-drop (dnd-kit): optimistic move + revert-on-failure + error

| Field | Value |
|-------|-------|
| **ID** | HTS-028 |
| **Type** | FE |
| **Epic** | EP-07 Kanban Board |
| **Story** | ST-02 Drag-and-drop |
| **Status** | TODO |
| **Depends on** | HTS-026, HTS-027 |
| **Blocks** | HTS-036 |
| **Traceability** | FR-B4, FR-B5, FR-B6; FR-K7; NFR-3; architecture.md §11 |

## Goal
Make board cards draggable between columns; dropping a card changes its state via the API,
optimistically moving it, and on failure reverting it to the previous column with an error.

## Scope
- In scope: dnd-kit wiring on the board; on drop, optimistic move + `PATCH state`; on success
  keep (and re-sort by modified); on failure revert to the original column + show an error
  (FR-B5); any-to-any drag (FR-B6); keyboard-accessible dnd if feasible.
- Out of scope: backend (HTS-027); filters (HTS-030).

## Technical approach
- dnd-kit columns as droppables, cards as draggables.
- Optimistic update of local board state; call `PATCH /api/tickets/{id}/state`.
- On rejection, roll back to the prior column and surface a non-blocking error (toast/inline).
- After success, the moved card sorts to the top of the target column (most-recently-modified).

## Acceptance criteria
- [ ] AC-1 — Dragging a card to another column calls the state API and moves it on success.
- [ ] AC-2 — On API failure, the card returns to its original column and an error is shown (FR-B5).
- [ ] AC-3 — Cards can be moved between any two columns (FR-B6).
- [ ] AC-4 — The change persists across a page refresh (state read back from server).

## Test plan
**Component (Vitest + RTL):**
- Positive: simulate drop → optimistic move + API called with the right target state.
- Negative: API rejects → card reverts to origin column + error rendered.
- Boundary: new→done direct move; rapid sequential moves don't desync local state.

**API-contract (MSW):**
- Mock PATCH success → card stays moved. Mock PATCH 400/500 → revert + error.

## How to run / verify
```bash
cd frontend && npm test -- drag-drop
npm run dev   # drag a card; simulate offline to see revert
```

## Definition of Done
- [ ] AC-1..AC-4 met
- [ ] Component + MSW tests pass (positive/negative/boundary)
- [ ] Revert-on-failure verified (FR-B5)
- [ ] INDEX.md status updated
