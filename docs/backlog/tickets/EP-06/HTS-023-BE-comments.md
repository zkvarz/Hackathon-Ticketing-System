# HTS-023 — [BE] Comment add + list: chronological, non-empty, immutable, no modified-at bump

| Field | Value |
|-------|-------|
| **ID** | HTS-023 |
| **Type** | BE |
| **Epic** | EP-06 Comments |
| **Story** | ST-01 Comments |
| **Status** | TODO |
| **Depends on** | HTS-019 |
| **Blocks** | HTS-024 |
| **Traceability** | FR-C1..C6; FR-K6 (cascade); architecture.md §6, §8 |

## Goal
Let authenticated users add non-empty comments to a ticket and list them oldest-first, with
author and created timestamp, without changing the ticket's `modified_at`.

## Scope
- In scope: `Comment` entity + repo (mapping the existing `comments` table); `GET/POST
  /api/tickets/{id}/comments`; non-empty body validation; author from security context;
  chronological ordering; ensure adding a comment does NOT bump ticket `modified_at`.
  > Note: the `comments` table and its `ON DELETE CASCADE` FK were created in HTS-019
  > (migration `V7__comments.sql`) to back the ticket-delete cascade test (FR-K6); HTS-023 maps
  > the entity onto it and adds no new table migration unless a column is required.
- Out of scope: edit/delete comments (stretch HTS-039/040); FE (HTS-024).

## Technical approach
- Comment fields per architecture.md §6; `author_id` from principal, `created_at` server-set.
- List ordered by `created_at ASC` (FR-C4).
- Comment writes must not touch the ticket entity's `modified_at` (FR-C5) — verified by test.
- Comments are deleted with their ticket via cascade (FR-K6, defined in HTS-019).
- Immutable: no PUT/DELETE endpoints in mandatory scope (FR-C6).

## Acceptance criteria
- [ ] AC-1 — Adding a non-empty comment persists it with author + created timestamp.
- [ ] AC-2 — Empty/whitespace-only body → 400.
- [ ] AC-3 — Listing returns comments oldest-first.
- [ ] AC-4 — Adding a comment does not change the ticket's `modified_at`.
- [ ] AC-5 — No edit/delete endpoints exist for comments (mandatory scope).

## Test plan
**Unit (JUnit 5 + Mockito):**
- Positive: add comment sets author from principal; list sorted ascending.
- Negative: blank body rejected; comment on non-existent ticket → 404.
- Boundary: single comment; many comments returned strictly in created order; body at max length accepted.

**Integration (Testcontainers — Postgres):**
- Add two comments with distinct timestamps → listed oldest-first.
- Capture ticket `modified_at`, add a comment, re-read ticket → `modified_at` unchanged (FR-C5).
- Delete the ticket → its comments are removed (cascade).

## How to run / verify
```bash
cd backend && ./mvnw test -Dtest='*Comment*'
curl -X POST localhost:8080/api/tickets/<id>/comments -H 'Content-Type: application/json' -d '{"body":"Reproduced in Chrome."}'
```

## Definition of Done
- [ ] AC-1..AC-5 met
- [ ] Unit tests (positive/negative/boundary) pass
- [ ] Testcontainers integration (ordering + modified-at-unchanged + cascade) passes
- [ ] INDEX.md status updated
