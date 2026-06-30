# HTS-015 — [BE] Team CRUD: validation, CI-unique name, 409 on delete-with-children

| Field | Value |
|-------|-------|
| **ID** | HTS-015 |
| **Type** | BE |
| **Epic** | EP-03 Teams |
| **Story** | ST-01 Team management |
| **Status** | TODO |
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
- [ ] AC-1 — Create with a valid unique name persists; list returns it.
- [ ] AC-2 — Empty/whitespace-only name → 400; duplicate (case/space-insensitive) → 409 `NAME_TAKEN`.
- [ ] AC-3 — Rename updates the name and `modified_at`; uniqueness re-checked.
- [ ] AC-4 — Delete of an empty team succeeds; delete of a team with tickets or epics → 409 `TEAM_HAS_CHILDREN`.

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
- [ ] AC-1..AC-4 met
- [ ] Unit tests (positive/negative/boundary) pass
- [ ] Testcontainers integration (CI uniqueness + 409 referenced delete) passes
- [ ] INDEX.md status updated
