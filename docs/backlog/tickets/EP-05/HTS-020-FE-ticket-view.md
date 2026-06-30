# HTS-020 — [FE] Ticket create/edit/details view

| Field | Value |
|-------|-------|
| **ID** | HTS-020 |
| **Type** | FE |
| **Epic** | EP-05 Tickets |
| **Story** | ST-01 Ticket CRUD |
| **Status** | TODO |
| **Depends on** | HTS-018, HTS-019 |
| **Blocks** | HTS-022, HTS-024, HTS-026 |
| **Traceability** | FR-S6, FR-K1..K3, FR-K6; NFR-3; architecture.md §11; wireframe image3 |

## Goal
Provide the ticket details/edit view: show all fields (incl. created-by/at, modified-at) and
allow editing type, team, epic, title, body, and state, plus create and delete-with-confirmation.

## Scope
- In scope: `/tickets/:id` details/edit view + a create mode; fields per spec; state dropdown
  with human-readable labels mapped to canonical API values; Save and Delete (confirm); loading/
  empty/error/success states; metadata display (created by/at, modified at).
- Out of scope: team→epic linkage behavior (HTS-022); comments panel (HTS-024); board (EP-07).

## Technical approach
- Form bound to the ticket DTO; state labels (e.g. "Ready for implementation") ↔ API values
  (`ready_for_implementation`).
- Save sends a PUT; surface 400 field errors; Delete confirms then routes back to the board.
- Matches wireframe image3 (metadata line, editable fields, Delete/Save).

## Acceptance criteria
- [ ] AC-1 — Details view shows all fields including created-by/at and modified-at.
- [ ] AC-2 — Create and edit submit valid payloads; server validation errors shown inline.
- [ ] AC-3 — State dropdown shows human-readable labels but sends canonical values.
- [ ] AC-4 — Delete asks for confirmation, then deletes and navigates away.

## Test plan
**Component (Vitest + RTL):**
- Positive: renders all fields; edit + save calls API with correct payload.
- Negative: blank title/body blocked; server 400 mapped to fields.
- Boundary: state label↔value mapping for all five states; body near max length handled.

**API-contract (MSW):**
- Mock get/put/delete; 400 field errors rendered; delete confirm → API called once.

## How to run / verify
```bash
cd frontend && npm test -- ticket-view
npm run dev   # open a ticket
```

## Definition of Done
- [ ] AC-1..AC-4 met
- [ ] Component + MSW tests pass (positive/negative/boundary)
- [ ] State label/value mapping verified for all five states
- [ ] INDEX.md status updated
