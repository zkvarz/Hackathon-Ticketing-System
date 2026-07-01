# HTS-044 — [BE] Trigram (`pg_trgm` GIN) index for title substring search

| Field | Value |
|-------|-------|
| **ID** | HTS-044 |
| **Type** | BE (performance) |
| **Epic** | EP-10 Improvements & Tech Debt |
| **Story** | ST-01 Board scale |
| **Status** | TODO |
| **Depends on** | HTS-029 |
| **Blocks** | — |
| **Traceability** | FR-B9, FR-B10; AMB-4, AMB-10; architecture.md §6, §7 |

## Goal
Make the board's case-insensitive title substring search (`?q=`) scale beyond the ≥100-ticket
bar by backing it with a trigram index, instead of a sequential scan.

## Context / why
HTS-029 implements `q` as `lower(title) LIKE lower('%…%')`. A leading-wildcard `LIKE` **cannot
use a b-tree index**, so it is a sequential scan on every keystroke's request. Fine at the
100-ticket bar (AMB-4), but it degrades linearly as boards grow. PostgreSQL's `pg_trgm`
extension provides a GIN index that accelerates `ILIKE '%…%'` substring matching.

## Scope
- In scope: enable the `pg_trgm` extension via Flyway; add a GIN trigram index on
  `lower(title)` (scoped by team where it helps); confirm the existing `search` query plan uses
  it; a query-plan / timing sanity test.
- Out of scope: full-text search / ranking; changing the query semantics (still AND-combined
  case-insensitive substring, FR-B9); pagination.

## Technical approach
- Flyway migration: `CREATE EXTENSION IF NOT EXISTS pg_trgm;` then
  `CREATE INDEX ix_tickets_title_trgm ON tickets USING gin (lower(title) gin_trgm_ops);`
  (consider a composite/partial strategy so team scoping still prunes first).
- No change to `TicketRepository.search` semantics — the planner picks the index up.
- Note the extension dependency in `architecture.md §7` (migrations) as it must exist in the
  Postgres image (it ships with `postgres:16`).

## Acceptance criteria
- [ ] AC-1 — `pg_trgm` extension + trigram index created by a versioned migration.
- [ ] AC-2 — The `?q=` search returns identical results to today (no semantic change).
- [ ] AC-3 — `EXPLAIN` shows the trigram index used for a substring search on a seeded large table (bitmap index scan, not seq scan).

## Test plan
**Integration (Testcontainers — Postgres):**
- Seed a few hundred tickets; assert `?q=` results match a reference filter (correctness unchanged).
- Run `EXPLAIN (FORMAT JSON)` for a substring query and assert the plan references the trigram index (or asserts no seq scan on `tickets`).

## How to run / verify
```bash
cd backend && ./mvnw test -Dtest='*Trigram*'
```

## Definition of Done
- [ ] AC-1..AC-3 met
- [ ] Migration is idempotent; extension present in the runtime image
- [ ] architecture.md §7 notes the extension
- [ ] INDEX.md status updated
