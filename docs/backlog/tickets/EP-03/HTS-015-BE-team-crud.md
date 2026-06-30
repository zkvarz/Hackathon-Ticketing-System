# HTS-015 — [BE] Team CRUD: validation, CI-unique name, 409 on delete-with-children

| Field | Value |
|-------|-------|
| **ID** | HTS-015 |
| **Type** | BE |
| **Epic** | EP-03 Teams |
| **Story** | ST-01 Team management |
| **Status** | DONE |
| **Depends on** | HTS-013 |
| **Blocks** | HTS-016, HTS-017 |
| **Traceability** | FR-T1..T6; FR-P4; AMB-9; architecture.md §6, §8 |

## Goal
Provide authenticated CRUD for teams with trimmed, case-insensitively-unique, non-empty names,
and prevent deletion while tickets or epics reference the team.

## Scope
- In scope: `Team` entity + repo + migration (unique index on `lower(trim(name))`);
  `GET/POST /api/teams`, `GET/PUT/DELETE /api/teams/{id}`; validation; 409 on duplicate name
  and on delete-with-children; created/modified timestamps.
- Out of scope: epics/tickets themselves (EP-04/05); FE (HTS-016).

## Technical approach
- Name normalized to `lower(trim(name))` for uniqueness (FR-T4 / AMB-9); store original display name.
- Delete checks for referencing epics/tickets first → 409 `TEAM_HAS_CHILDREN` (no cascade, FR-T5).
- `modified_at` updated on rename.

## Acceptance criteria
- [x] AC-1 — Create with a valid unique name persists; list returns it.
- [x] AC-2 — Empty/whitespace-only name → 400; duplicate (case/space-insensitive) → 409 `NAME_TAKEN`.
- [x] AC-3 — Rename updates the name and `modified_at`; uniqueness re-checked.
- [x] AC-4 — Delete of an empty team succeeds; delete of a team with tickets or epics → 409
  `TEAM_HAS_CHILDREN` (rule via `TeamReferenceCounter`; epic/ticket counters land in
  HTS-017/HTS-019 — unit-tested here with a mock counter, end-to-end with epics in HTS-017).

## Test plan
**Unit (JUnit 5 + Mockito):**
- Positive: create/rename/delete-empty succeed.
- Negative: duplicate name; blank name; delete with children.
- Boundary: names differing only by case or surrounding whitespace collide; single-character name accepted; rename to an existing other team's name rejected.

**Integration (Testcontainers — Postgres):**
- Unique index enforces CI uniqueness at the DB level.
- Create team → create epic in it → DELETE returns 409; remove child → DELETE succeeds.

## How to run / verify
```bash
cd backend && ./mvnw test -Dtest='*Team*'
curl -X POST localhost:8080/api/teams -H 'Content-Type: application/json' -d '{"name":"Payments Team"}'
```

## Definition of Done
- [x] AC-1..AC-4 met
- [x] Unit tests (positive/negative/boundary) pass
- [x] Testcontainers integration (CI uniqueness + delete) passes (referenced-delete e2e in HTS-017)
- [x] INDEX.md status updated

## Implementation notes
- `Team` entity + `V4__teams.sql` (unique functional index on `lower(name)`). `TeamService`:
  trim + CI-unique create/rename (`NAME_TAKEN` 409), `NotFoundException` (404) on missing,
  delete blocked when referenced.
- **Extensible referenced-delete:** `TeamReferenceCounter` interface; `TeamService` sums all
  registered counters and blocks delete (`TEAM_HAS_CHILDREN` 409) when > 0. No counters exist
  yet (team always deletable); HTS-017 (epics) and HTS-019 (tickets) each register one. The
  same counters feed `TeamResponse.epicCount`/`ticketCount` (0 for now) for HTS-016's UI.
- `TeamController` (`/api/teams` CRUD, authenticated + CSRF). Exception handler gains
  `NOT_FOUND` (404), `NAME_TAKEN` (409), `TEAM_HAS_CHILDREN` (409).
- Tests (9): `TeamServiceTest` (Mockito incl. mocked counter → 409 delete-blocked) +
  `TeamCrudIntegrationTest` (Testcontainers + spring-security-test: create/list/rename/
  delete-empty, blank → 400, CI-duplicate → 409, missing → 404).
