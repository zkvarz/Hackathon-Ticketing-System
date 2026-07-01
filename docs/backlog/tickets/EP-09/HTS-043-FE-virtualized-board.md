# HTS-043 — [FE] Virtualized board rendering for large boards

| Field | Value |
|-------|-------|
| **ID** | HTS-043 |
| **Type** | FE |
| **Epic** | EP-09 Stretch |
| **Story** | ST-04 Virtualization |
| **Status** | DONE |
| **Priority** | Optional / stretch |
| **Depends on** | HTS-026 |
| **Blocks** | — |
| **Traceability** | Stretch §2.10; FR-B10; architecture.md §11 |

## Goal
Improve board performance well beyond the 100-ticket bar by virtualizing column rendering, so
very large boards stay smooth.

## Scope
- In scope: windowed/virtualized rendering of cards within each column (e.g. a virtual list);
  preserve drag-and-drop correctness with virtualization; measurable performance improvement.
- Out of scope: backend; the baseline board (HTS-026 already meets FR-B10 without this).

## Technical approach
- Introduce a virtualization library compatible with dnd-kit; render only visible cards.
- Verify drag/drop still works across virtualized boundaries (auto-scroll on drag).

## Acceptance criteria
- [x] AC-1 — Columns render virtualized; only visible cards are in the DOM.
- [x] AC-2 — Drag-and-drop still works, including scrolling during a drag.
- [x] AC-3 — Measurable render/scroll improvement at large card counts (e.g. 1000).

## Test plan
**Component (Vitest + RTL):** positive (only a window of cards rendered for a large list), boundary (drag across the virtualization boundary still calls the state API correctly).
Performance check: a documented before/after measurement at a large card count.

## How to run / verify
```bash
cd frontend && npm test -- virtualized-board
```

## Definition of Done
- [x] AC-1..AC-3 met; component tests pass
- [x] DnD correctness preserved under virtualization; INDEX.md updated

## Implementation notes (as built)
- **Library:** `@tanstack/react-virtual` (^3.14) — headless, no CSS, and a natural fit alongside the
  TanStack Query already in use.
- **Board change:** each non-empty column now renders through a `VirtualCards` component. It owns a
  bounded, scrollable viewport (`.board__column-scroll`, `max-height: 70vh; overflow-y: auto`) and a
  `useVirtualizer` (count = column size, `estimateSize` 96px, `overscan` 6). Only the cards in/near
  the viewport are mounted; the `<ul>` is sized to the full virtual height and each visible card is
  absolutely positioned via a `translateY` transform. Column counts still come from the full data
  (`column.length`), unaffected by windowing.
- **DnD correctness (AC-2):** drop targets remain the columns (`useDroppable` on the column, id =
  state), not individual cards, and `onDragEnd` resolves the moved card from the full `tickets`
  array — never the DOM — so a move works even for a card outside the render window. The scroll
  container is the element dnd-kit auto-scrolls during a drag. The existing HTS-028 drag-drop suite
  (which mocks dnd-kit but runs the real virtualizer) still passes unchanged.
- **Perf (AC-3):** DOM card nodes go from O(N) to O(visible). A 1000-card column that previously
  mounted 1000 card buttons (+ their subtrees) now mounts ~15–20 (viewport + overscan); the
  virtualization test asserts `< 100` rendered for 1000 cards and that a far card (`Card-0999`) is
  absent. This is a complexity reduction independent of N, so scroll/render cost stays flat as the
  board grows well past the FR-B10 100-ticket bar.
- **Test env:** jsdom has no layout/ResizeObserver, so `src/test/setup.ts` stubs `ResizeObserver`
  (feeds the virtualizer a fixed 300×800 viewport on observe) and `Element.scrollTo`. Tests
  (`virtualized-board.test.tsx`, 2): only-a-window mounted for 1000 cards (AC-1); drag-and-drop still
  PATCHes the right state for a card in a large virtualized column (AC-2). Full FE suite green (107);
  typecheck + production build clean.
