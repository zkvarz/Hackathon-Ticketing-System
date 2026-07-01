# HTS-027 — [BE] State-change endpoint: validate enum, persist, advance modified-at

| Field | Value |
|-------|-------|
| **ID** | HTS-027 |
| **Type** | BE |
| **Epic** | EP-07 Kanban Board |
| **Story** | ST-02 Drag-and-drop |
| **Status** | DONE |
| **Depends on** | HTS-019 |
| **Blocks** | HTS-028 |
| **Traceability** | FR-K7, FR-B4, FR-B6; FR-P1; AMB-3; architecture.md §8 |

## Goal
Provide a focused endpoint to change a ticket's state (used by board drag-and-drop) that
validates the target state, persists immediately, and advances `modified_at`.

## Scope
- In scope: `PATCH /api/tickets/{id}/state` accepting a canonical target state; enum validation;
  immediate persistence (FR-K7); `modified_at` advanced (state is a tracked change, AMB-3);
  any-to-any transition allowed (FR-B6).
- Out of scope: full ticket edit (HTS-019); FE drag interaction (HTS-028).

## Technical approach
- Accept `{ "state": "<canonical>" }`; validate against the five values (FR-K8); reject unknown → 400.
- No sequential-transition enforcement (FR-B6) — any valid state is accepted.
- Update state + `modified_at`; return the updated ticket (so the FE can re-sort).
- 404 if the ticket doesn't exist.

## Acceptance criteria
- [x] AC-1 — Valid state change persists immediately and advances `modified_at`.
- [x] AC-2 — Invalid/unknown state → 400 (standard error model).
- [x] AC-3 — Any-to-any transition is accepted (no sequential constraint).
- [x] AC-4 — Non-existent ticket → 404.

## Test plan
**Unit (JUnit 5 + Mockito):**
- Positive: each valid target state accepted; modified_at advanced (fixed clock).
- Negative: invalid enum rejected; missing ticket → 404.
- Boundary: new→done directly (skipping middle states) allowed; setting the same state still persists (and advances modified_at, since it's an explicit change request — document the choice).

**Integration (Testcontainers — Postgres):**
- PATCH state → re-read shows new state + later modified_at; the ticket's board column would change accordingly.

## How to run / verify
```bash
cd backend && ./mvnw test -Dtest='*State*'
curl -X PATCH localhost:8080/api/tickets/<id>/state -H 'Content-Type: application/json' -d '{"state":"in_progress"}'
```

## Definition of Done
- [x] AC-1..AC-4 met
- [x] Unit tests (positive/negative/boundary) pass
- [x] Testcontainers integration (persist + modified-at) passes
- [x] Same-state PATCH behavior documented
- [x] INDEX.md status updated
