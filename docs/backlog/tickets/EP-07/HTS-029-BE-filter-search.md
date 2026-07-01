# HTS-029 — [BE] Server-side filter (type, epic) + title substring search (AND)

| Field | Value |
|-------|-------|
| **ID** | HTS-029 |
| **Type** | BE |
| **Epic** | EP-07 Kanban Board |
| **Story** | ST-03 Filters & search |
| **Status** | DONE |
| **Depends on** | HTS-025 |
| **Blocks** | HTS-030 |
| **Traceability** | FR-B9; AMB-10; architecture.md §8 |

## Goal
Extend the board query with optional server-side filters by ticket type and epic plus a
case-insensitive substring search over title, combined with AND logic.

## Scope
- In scope: `GET /api/tickets?teamId=…&type=&epicId=&q=`; each param optional; AND-combined;
  case-insensitive title `LIKE`/`ILIKE` on `q`; keeps modified-desc ordering.
- Out of scope: FE controls (HTS-030).

## Technical approach
- Build the query dynamically (Specification/criteria) adding predicates only for present params (AMB-10: server-side).
- `q` → case-insensitive substring on title (FR-B9); `type`/`epicId` → equality.
- All predicates AND-combined; result still ordered modified desc.

## Acceptance criteria
- [x] AC-1 — `type` filter returns only that type; `epicId` filter only that epic.
- [x] AC-2 — `q` matches case-insensitively as a substring of title.
- [x] AC-3 — Multiple params combine with AND.
- [x] AC-4 — No params → same as the unfiltered board query.

## Test plan
**Unit (JUnit 5 + Mockito):**
- Positive: each single filter applied; combined filters AND together.
- Negative: invalid `type` enum → 400; unknown `epicId` → empty result (valid).
- Boundary: empty `q` ignored (not treated as match-nothing); `q` matching mixed case; `q` matching a substring mid-title.

**Integration (Testcontainers — Postgres):**
- Seed varied tickets; assert each filter and the AND-combination return the expected subset, still ordered modified desc.

## How to run / verify
```bash
cd backend && ./mvnw test -Dtest='*Filter*'
curl 'localhost:8080/api/tickets?teamId=<uuid>&type=bug&q=payment'
```

## Definition of Done
- [x] AC-1..AC-4 met
- [x] Unit tests (positive/negative/boundary) pass
- [x] Testcontainers integration (filters + AND + ordering) passes
- [x] INDEX.md status updated
