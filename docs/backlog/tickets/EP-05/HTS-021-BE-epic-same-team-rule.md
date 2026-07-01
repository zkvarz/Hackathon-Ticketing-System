# HTS-021 — [BE] Epic-same-team enforcement + team-change epic-reset validation

| Field | Value |
|-------|-------|
| **ID** | HTS-021 |
| **Type** | BE |
| **Epic** | EP-05 Tickets |
| **Story** | ST-02 References & team-change |
| **Status** | DONE |
| **Depends on** | HTS-019 |
| **Blocks** | HTS-022 |
| **Traceability** | FR-E7, FR-K5; architecture.md §6, §8 |

## Goal
Enforce that a ticket's epic (when set) belongs to the same team as the ticket, including when
the ticket's team changes — the backend must reject a ticket whose epic belongs to a different team.

## Scope
- In scope: validation on create and update that `epic.team_id == ticket.team_id` when `epic_id`
  is non-null; rejection of a team change that leaves a now-cross-team epic; clear error code.
- Out of scope: FE dropdown behavior (HTS-022).

## Technical approach
- On create/update: if `epic_id` set, load epic and compare team → mismatch → 400 `EPIC_TEAM_MISMATCH` (FR-E7).
- On team change: if the existing epic no longer matches the new team, the request must either
  clear the epic or supply a same-team epic; otherwise reject (FR-K5). The backend never
  silently reassigns — the client (HTS-022) clears/replaces the epic.
- Null epic always allowed.

## Acceptance criteria
- [x] AC-1 — Creating/updating a ticket with an epic from the same team succeeds.
- [x] AC-2 — Creating/updating with an epic from a different team → 400 `EPIC_TEAM_MISMATCH`.
- [x] AC-3 — Changing team while keeping a now-cross-team epic is rejected; clearing the epic (or choosing a same-team epic) succeeds.
- [x] AC-4 — Null epic is always accepted.

## Test plan
**Unit (JUnit 5 + Mockito):**
- Positive: same-team epic accepted; null epic accepted.
- Negative: cross-team epic rejected; team change leaving cross-team epic rejected.
- Boundary: team change with epic simultaneously cleared in the same request succeeds; team change with epic replaced by a same-team epic succeeds.

**Integration (Testcontainers — Postgres):**
- Two teams each with an epic; attempt to attach team A's epic to a team B ticket → 400; move ticket to team A with that epic → success.

## How to run / verify
```bash
cd backend && ./mvnw test -Dtest='*EpicTeam*'
```

## Definition of Done
- [x] AC-1..AC-4 met
- [x] Unit tests (positive/negative/boundary) pass
- [x] Testcontainers integration (cross-team rejection + team-move) passes
- [x] INDEX.md status updated
