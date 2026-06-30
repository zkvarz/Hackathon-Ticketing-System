# HTS-041 — [BE] Ticket activity history (backend)

| Field | Value |
|-------|-------|
| **ID** | HTS-041 |
| **Type** | BE |
| **Epic** | EP-09 Stretch |
| **Story** | ST-03 Activity history |
| **Status** | TODO |
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
- [ ] AC-1 — Creating/editing/state-changing a ticket appends accurate activity rows.
- [ ] AC-2 — Activity is returned chronologically with actor + field + old/new + timestamp.
- [ ] AC-3 — A no-op save (no field change) appends no activity (consistent with AMB-3).
- [ ] AC-4 — History is append-only (no edit/delete endpoints).

## Test plan
**Unit (JUnit + Mockito):** positive (each change type logged), negative (no-op logs nothing), boundary (multi-field edit logs one row per changed field; unchanged fields excluded).
**Integration (Testcontainers — Postgres):** perform a sequence of changes → activity rows match, ordered; transactional consistency (failed update writes no activity).

## How to run / verify
```bash
cd backend && ./mvnw test -Dtest='*Activity*'
```

## Definition of Done
- [ ] AC-1..AC-4 met; unit + integration tests pass (positive/negative/boundary)
- [ ] Data-model addition noted in architecture.md; INDEX.md updated
