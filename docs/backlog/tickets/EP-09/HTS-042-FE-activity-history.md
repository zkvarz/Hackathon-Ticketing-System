# HTS-042 — [FE] Ticket activity history (frontend)

| Field | Value |
|-------|-------|
| **ID** | HTS-042 |
| **Type** | FE |
| **Epic** | EP-09 Stretch |
| **Story** | ST-03 Activity history |
| **Status** | TODO |
| **Priority** | Optional / stretch |
| **Depends on** | HTS-041, HTS-020 |
| **Blocks** | — |
| **Traceability** | Stretch §2.10; NFR-3; architecture.md §11 |

## Goal
Display a ticket's activity history (who changed what, when) in the ticket view.

## Scope
- In scope: an activity/history section or tab on `/tickets/:id`; chronological list with
  actor, field, old→new, timestamp; loading/empty/error states.
- Out of scope: backend (HTS-041).

## Acceptance criteria
- [ ] AC-1 — Activity renders chronologically with actor/field/old→new/time.
- [ ] AC-2 — Empty state shown when there's no activity yet.
- [ ] AC-3 — Loading/error states handled.

## Test plan
**Component (Vitest + RTL):** positive (renders entries in order), negative (error state), boundary (empty list → empty state).
**API-contract (MSW):** mock activity (empty + populated) + error.

## How to run / verify
```bash
cd frontend && npm test -- activity
```

## Definition of Done
- [ ] AC-1..AC-3 met; component + MSW tests pass
- [ ] INDEX.md status updated
