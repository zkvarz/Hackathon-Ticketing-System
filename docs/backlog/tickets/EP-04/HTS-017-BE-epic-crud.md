# HTS-017 — [BE] Epic CRUD: team-fixed, title validation, 409 on delete-with-tickets

| Field | Value |
|-------|-------|
| **ID** | HTS-017 |
| **Type** | BE |
| **Epic** | EP-04 Epics |
| **Story** | ST-01 Epic management |
| **Status** | TODO |
| **Depends on** | HTS-015 |
| **Blocks** | HTS-018, HTS-019 |
| **Traceability** | FR-E1..E5, FR-E8; FR-P4; architecture.md §6, §8 |

## Goal
Provide authenticated CRUD for epics that each belong to exactly one (immutable) team, with a
non-empty title and optional description, blocking deletion while tickets reference the epic.

## Scope
- In scope: `Epic` entity + repo + migration (FK to team); `GET/POST /api/epics?teamId=…`,
  `GET/PUT/DELETE /api/epics/{id}`; title validation; team immutable after creation; 409 on
  delete-with-tickets.
- Out of scope: ticket→epic linkage rules (HTS-021); FE (HTS-018).

## Technical approach
- Team set at creation; PUT ignores/rejects any team change (FR-E2) → 400 `EPIC_TEAM_IMMUTABLE` if attempted.
- Title trimmed + non-empty (FR-E5), length ≤200 (consistent with AMB-1 title rule).
- Delete checks referencing tickets → 409 `EPIC_HAS_TICKETS` (FR-E8).
- List filtered by `teamId`.

## Acceptance criteria
- [ ] AC-1 — Create with team + non-empty title persists; list by team returns it.
- [ ] AC-2 — Blank/whitespace title → 400; over-length title → 400.
- [ ] AC-3 — Editing title/description works; attempting to change team → 400 `EPIC_TEAM_IMMUTABLE`.
- [ ] AC-4 — Delete of an unreferenced epic succeeds; with referencing tickets → 409 `EPIC_HAS_TICKETS`.

## Test plan
**Unit (JUnit 5 + Mockito):**
- Positive: create/edit/delete-unreferenced succeed.
- Negative: blank title; team-change attempt; delete with referencing tickets.
- Boundary: title length 200 (accept) vs 201 (reject); empty optional description allowed; description at max length allowed.

**Integration (Testcontainers — Postgres):**
- Create team + epic; create a ticket referencing the epic → DELETE epic returns 409; delete the ticket → DELETE epic succeeds.
- FK prevents creating an epic for a non-existent team (400/404).

## How to run / verify
```bash
cd backend && ./mvnw test -Dtest='*Epic*'
curl -X POST 'localhost:8080/api/epics?teamId=<uuid>' -H 'Content-Type: application/json' -d '{"title":"Checkout reliability"}'
```

## Definition of Done
- [ ] AC-1..AC-4 met
- [ ] Unit tests (positive/negative/boundary) pass
- [ ] Testcontainers integration (immutable team + 409 referenced delete) passes
- [ ] INDEX.md status updated
