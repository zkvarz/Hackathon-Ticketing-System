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
- [x] AC-1 — Dragging a card to another column calls the state API and moves it on success.
- [x] AC-2 — On API failure, the card returns to its original column and an error is shown (FR-B5).
- [x] AC-3 — Cards can be moved between any two columns (FR-B6).
- [x] AC-4 — The change persists across a page refresh (state read back from server).

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
- [x] AC-1..AC-4 met
- [x] Component + MSW tests pass (positive/negative/boundary) — `drag-drop.test.tsx` (4)
- [x] Revert-on-failure verified (FR-B5) — optimistic snapshot rolled back + error toast
- [x] INDEX.md status updated

## Implementation notes (as built)
- `@dnd-kit/core` (Pointer + Keyboard sensors; Pointer has a 5px activation distance so a plain
  click still opens the ticket). Columns are droppables keyed by state; cards are draggables
  keyed by ticket id.
- `onDragEnd` runs an optimistic `useMutation`: `onMutate` snapshots the exact board cache key
  and moves the card to the front of the target column (top = most-recently-modified); the PATCH
  hits `/tickets/{id}/state`; `onError` restores the snapshot and shows a "Move failed — returned
  to its column" toast; `onSuccess` invalidates `['tickets']` to re-sync the authoritative order
  (AC-4 persistence).
- Test approach: `@dnd-kit/core` is mocked to capture `onDragEnd` (jsdom can't measure layout for
  a real pointer drag), so the real optimistic/revert logic is exercised via a stateful MSW board.
- `ToastProvider` moved into the router root so the board (and `App.test` mounting `routes`) share
  one toaster; `renderWithProviders` now supplies it for component tests.
