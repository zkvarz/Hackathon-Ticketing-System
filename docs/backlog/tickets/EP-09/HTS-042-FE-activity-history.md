# HTS-042 — [FE] Ticket activity history (frontend)

| Field | Value |
|-------|-------|
| **ID** | HTS-042 |
| **Type** | FE |
| **Epic** | EP-09 Stretch |
| **Story** | ST-03 Activity history |
| **Status** | DONE |
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
- [x] AC-1 — Activity renders chronologically with actor/field/old→new/time.
- [x] AC-2 — Empty state shown when there's no activity yet.
- [x] AC-3 — Loading/error states handled.

## Test plan
**Component (Vitest + RTL):** positive (renders entries in order), negative (error state), boundary (empty list → empty state).
**API-contract (MSW):** mock activity (empty + populated) + error.

## How to run / verify
```bash
cd frontend && npm test -- activity
```

## Definition of Done
- [x] AC-1..AC-3 met; component + MSW tests pass
- [x] INDEX.md status updated

## Implementation notes (as built)
- `api/activity.ts`: `TicketActivity` interface (id, ticketId, actorEmail, field, oldValue,
  newValue, `at`) + `listActivity(ticketId)` → `GET /api/tickets/{id}/activity` (read-only, the
  HTS-041 endpoint).
- `ActivityPanel` (new): a read-only section on the ticket view that queries `['activity',
  ticketId]` and renders an ordered list oldest-first (the backend already sorts by `seq`). Each
  entry shows the actor email + a human-readable sentence: the `created` entry reads "created the
  ticket"; every other field reads "changed the &lt;noun&gt; from “old” to “new”". `state`/`type`
  values are mapped through `TICKET_STATE_LABELS`/`TICKET_TYPE_LABELS` (never the wire values);
  free-text fields (title/body/team/epic) show the captured strings, null → "—". Loading/empty/error
  states reuse the shared `Loading`/`Empty`/`ErrorState` components (HTS-032).
- `TicketDetailsPage`: renders `<ActivityPanel ticketId={id} />` below the comments panel, only for
  a persisted ticket (edit mode). Added a default `/api/tickets/1/activity` handler to that page's
  test `baseHandlers()` (MSW runs with `onUnhandledRequest: 'error'`).
- Tests (`activity.test.tsx`, 3): chronological render with actor + labels + old→new (AC-1), empty
  state (AC-2), 500 → error state (AC-3). Full FE suite green (105); typecheck clean.
