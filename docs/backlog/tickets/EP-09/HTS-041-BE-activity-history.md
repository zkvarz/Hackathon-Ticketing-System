# HTS-041 — [BE] Ticket activity history (backend)

| Field | Value |
|-------|-------|
| **ID** | HTS-041 |
| **Type** | BE |
| **Epic** | EP-09 Stretch |
| **Story** | ST-03 Activity history |
| **Status** | DONE |
| **Priority** | Optional / stretch |
| **Depends on** | HTS-019 |
| **Blocks** | HTS-042 |
| **Traceability** | Stretch §2.10; architecture.md §6, §8 |

## Goal
Record an append-only history of ticket changes (field/state transitions) and expose it for
display.

## Scope
- In scope: `ticket_activity` table (ticket_id, actor, field, old→new, at); record entries on
  ticket create/edit/state-change; `GET /api/tickets/{id}/activity` ordered chronologically.
- Out of scope: FE (HTS-042); editing/deleting history (append-only).

## Technical approach
- Hook into the ticket service (HTS-019/027) to append activity rows within the same transaction.
- Capture actor from the security context and the changed field old/new values.

## Acceptance criteria
- [x] AC-1 — Creating/editing/state-changing a ticket appends accurate activity rows.
- [x] AC-2 — Activity is returned chronologically with actor + field + old/new + timestamp.
- [x] AC-3 — A no-op save (no field change) appends no activity (consistent with AMB-3).
- [x] AC-4 — History is append-only (no edit/delete endpoints).

## Test plan
**Unit (JUnit + Mockito):** positive (each change type logged), negative (no-op logs nothing), boundary (multi-field edit logs one row per changed field; unchanged fields excluded).
**Integration (Testcontainers — Postgres):** perform a sequence of changes → activity rows match, ordered; transactional consistency (failed update writes no activity).

## How to run / verify
```bash
cd backend && ./mvnw test -Dtest='*Activity*'
```

## Definition of Done
- [x] AC-1..AC-4 met; unit + integration tests pass (positive/negative/boundary)
- [x] Data-model addition noted in architecture.md; INDEX.md updated

## Implementation notes (as built)
- **Schema:** `V12__ticket_activity.sql` adds an append-only `ticket_activity` table (id, `seq`
  identity, ticket_id FK `ON DELETE CASCADE`, actor_email, field, old_value/new_value text,
  occurred_at). A DB-assigned monotonic `seq` orders rows — several rows from one multi-field edit
  share an `occurred_at`, and a UUIDv7 id is only millisecond-ordered so it cannot tie-break within
  a millisecond; ordering by `seq` preserves insertion (chronological) order.
- **Entity/repo:** `TicketActivity` (immutable, does not extend `BaseEntity` — no `modified_at`,
  mirrors `Comment`); `seq` mapped `insertable=false` (DB fills it). Read via
  `findByTicket_IdOrderBySeqAsc`.
- **Recording (same transaction as the mutation):** `TicketService.create` appends one `created`
  row; `update` uses the new `Ticket.applyChangesTracked(...)` (returns one `TicketFieldChange` per
  changed field, stable field order team→epic→type→state→title→body) and appends one row per change,
  all stamped with one clock instant — a no-op edit records nothing (AC-3). `changeState` appends a
  single `state` row **only on a real transition** (re-setting the same state still bumps
  `modified_at` per HTS-027 but is not history-worthy). `applyChanges` (boolean) now delegates to
  `applyChangesTracked`, so the existing AMB-3 contract/tests are unchanged.
- **Actor:** taken from the authenticated principal (`Authentication.getName()` in the controller,
  normalized) — `update`/`changeState` gained an `actorEmail` parameter; never from the client.
  Old/new values are captured as display strings (team/epic name, enum wire value, raw title/body)
  so the log stays accurate if a team/epic is later renamed/removed.
- **API:** `GET /api/tickets/{id}/activity` → `TicketActivityResponse[]` (id, ticketId, actorEmail,
  field, oldValue, newValue, `at`), oldest-first; 404 if the ticket is missing.
- **Tests:** `TicketActivityServiceTest` (6, Mockito + ArgumentCaptor: created row, one-row-per-
  changed-field, no-op logs nothing, state transition, same-state logs nothing, ordered read).
  `TicketActivityIntegrationTest` (5, Postgres: created entry, full create→edit→state sequence
  chronological with actor + old/new, no-op records nothing, failed update rolls back with no
  activity, missing ticket → 404). Full backend suite green (191).
