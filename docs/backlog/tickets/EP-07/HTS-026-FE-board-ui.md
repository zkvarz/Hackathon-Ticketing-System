# HTS-026 — [FE] Board UI: 5 columns, cards (title+type+epic), team selector, counts

| Field | Value |
|-------|-------|
| **ID** | HTS-026 |
| **Type** | FE |
| **Epic** | EP-07 Kanban Board |
| **Story** | ST-01 Board view |
| **Status** | DONE |
| **Depends on** | HTS-020, HTS-025 |
| **Blocks** | HTS-028, HTS-030, HTS-043 |
| **Traceability** | FR-B1, FR-B2, FR-B3, FR-B8, FR-B10; FR-S5; NFR-3; wireframe image1 |

## Goal
Render the Kanban board for a selected team: five columns in workflow order, cards showing
title/type (and epic), a team selector, per-column counts, and entry points to create/open tickets.

## Scope
- In scope: `/board` route; team selector (FR-S5); five columns in workflow order (FR-B2);
  cards showing title + type + epic (FR-B3); column counts; "New ticket" + open-existing
  navigation (FR-B8); loading/empty/error states; remains usable at 100+ cards (FR-B10).
- Out of scope: drag-drop (HTS-028); filters/search (HTS-030); virtualization (stretch HTS-043).

## Technical approach
- Fetch via the board query (HTS-025); group tickets by state into the five columns; order
  within column by modified desc (data already sorted).
- Card component per wireframe image1 (type badge, title, epic, relative time).
- New-ticket button routes to create; clicking a card opens `/tickets/:id`.

## Acceptance criteria
- [x] AC-1 — Five columns render in workflow order with correct human-readable labels.
- [x] AC-2 — Cards appear in the right column for their state, modified-desc within a column.
- [x] AC-3 — Team selector switches the board to that team's tickets.
- [x] AC-4 — Create and open-ticket entry points work; empty/loading/error states render.

## Test plan
**Component (Vitest + RTL):**
- Positive: given board data, cards land in the correct columns with counts.
- Negative: API error → error state; empty team → five empty columns + empty hint.
- Boundary: a ticket in each of the five states; large list (e.g. 100 cards) renders without error.

**API-contract (MSW):**
- Mock board data per team; mock error; switching team triggers refetch.

## How to run / verify
```bash
cd frontend && npm test -- BoardPage
npm run dev   # /board
```

## Definition of Done
- [x] AC-1..AC-4 met
- [x] Component + MSW tests pass (positive/negative/boundary)
- [x] Layout/labels match wireframe image1; 100-card render verified
- [x] INDEX.md status updated
