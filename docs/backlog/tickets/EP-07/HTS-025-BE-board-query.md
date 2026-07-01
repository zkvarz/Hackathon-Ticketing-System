# HTS-025 — [BE] Board query: tickets by team, 5 states, most-recently-modified order

| Field | Value |
|-------|-------|
| **ID** | HTS-025 |
| **Type** | BE |
| **Epic** | EP-07 Kanban Board |
| **Story** | ST-01 Board view |
| **Status** | DONE |
| **Depends on** | HTS-019 |
| **Blocks** | HTS-026, HTS-029 |
| **Traceability** | FR-B1, FR-B2, FR-B7; FR-B10; architecture.md §8 |

## Goal
Provide the query that returns a team's tickets for the board, ordered most-recently-modified
first, suitable for grouping into the five state columns and performant at ≥100 tickets.

## Scope
- In scope: `GET /api/tickets?teamId=…` returning the team's tickets with the fields the board
  needs (id, title, type, state, epic ref/title, modified_at); ordering by `modified_at DESC`
  (FR-B7); index to keep it fast at scale (FR-B10).
- Out of scope: filters/search params (HTS-029); state change (HTS-027).

## Technical approach
- Repository query filtered by `team_id`, ordered `modified_at DESC`.
- DB index on `(team_id, modified_at DESC)` for the 100+ ticket bar (FR-B10 / AMB-4).
- Response shape lets the FE group by `state` into the five columns (FR-B2).

## Acceptance criteria
- [x] AC-1 — Returns only the requested team's tickets.
- [x] AC-2 — Ordered by `modified_at` descending.
- [x] AC-3 — Includes the fields the board card needs (title, type, epic, state, modified).
- [x] AC-4 — Performs acceptably with 100+ tickets (indexed query).

## Test plan
**Unit (JUnit 5 + Mockito):**
- Positive: query invoked with the right team filter and sort.
- Negative: missing/invalid teamId → 400; unknown team → empty list (or 404 per chosen convention).
- Boundary: team with 0 tickets → empty; ordering stable when modified_at ties (tie-break by id).

**Integration (Testcontainers — Postgres):**
- Seed (via API) tickets across states with varied modified_at → result ordered desc.
- Seed 100+ tickets → query returns within a reasonable time and uses the index (sanity).

## How to run / verify
```bash
cd backend && ./mvnw test -Dtest='*Board*'
curl 'localhost:8080/api/tickets?teamId=<uuid>'
```

## Definition of Done
- [x] AC-1..AC-4 met
- [x] Unit tests (positive/negative/boundary) pass
- [x] Testcontainers integration (ordering + 100-ticket sanity) passes
- [x] Index present for `(team_id, modified_at)`
- [x] INDEX.md status updated
