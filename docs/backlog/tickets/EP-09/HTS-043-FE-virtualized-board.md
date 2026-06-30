# HTS-043 — [FE] Virtualized board rendering for large boards

| Field | Value |
|-------|-------|
| **ID** | HTS-043 |
| **Type** | FE |
| **Epic** | EP-09 Stretch |
| **Story** | ST-04 Virtualization |
| **Status** | TODO |
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
- [ ] AC-1 — Columns render virtualized; only visible cards are in the DOM.
- [ ] AC-2 — Drag-and-drop still works, including scrolling during a drag.
- [ ] AC-3 — Measurable render/scroll improvement at large card counts (e.g. 1000).

## Test plan
**Component (Vitest + RTL):** positive (only a window of cards rendered for a large list), boundary (drag across the virtualization boundary still calls the state API correctly).
Performance check: a documented before/after measurement at a large card count.

## How to run / verify
```bash
cd frontend && npm test -- virtualized-board
```

## Definition of Done
- [ ] AC-1..AC-3 met; component tests pass
- [ ] DnD correctness preserved under virtualization; INDEX.md updated
