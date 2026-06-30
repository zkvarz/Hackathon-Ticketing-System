# HTS-019 — [BE] Ticket CRUD: fields, enum validation, created/modified metadata, modified-at semantics, cascade comments

| Field | Value |
|-------|-------|
| **ID** | HTS-019 |
| **Type** | BE |
| **Epic** | EP-05 Tickets |
| **Story** | ST-01 Ticket CRUD |
| **Status** | TODO |
| **Depends on** | HTS-017 |
| **Blocks** | HTS-020, HTS-021, HTS-023, HTS-025, HTS-027 |
| **Traceability** | FR-K1..K4, FR-K6, FR-K8; AMB-1, AMB-3; architecture.md §6, §8 |

## Goal
Provide authenticated CRUD for tickets with all required fields, server-side enum/reference
validation, server-set created/modified metadata with correct "real change only" semantics, and
delete that cascades comments.

## Scope
- In scope: `Ticket` entity + repo + migration; `GET /api/tickets?teamId=…`,
  `POST /api/tickets`, `GET/PUT/DELETE /api/tickets/{id}`; enum validation (type, state);
  `created_by` from the authenticated user; `created_at`/`modified_at` server-set UTC;
  modified-at advances only on actual field/state change (AMB-3); delete cascades comments (FR-K6).
- Out of scope: epic same-team rule + team-change reset (HTS-021); board ordering/filter
  (EP-07); state-change endpoint for drag-drop (HTS-027, though PUT also persists state).

## Technical approach
- Fields + limits per architecture.md §6 (title ≤200, body ≤10000, non-empty trimmed).
- Enum validation server-side (FR-K8): `type ∈ {bug,feature,fix}`, `state ∈` the five values.
- On update, diff incoming vs stored; only bump `modified_at` if a tracked field actually
  changed (AMB-3) — saving identical values is a no-op for the timestamp.
- `created_by` taken from the security context, never the client.
- Delete cascades comments (DB `ON DELETE CASCADE` or service-level) (FR-K6).

## Acceptance criteria
- [ ] AC-1 — Create persists all fields; `created_by`/`created_at` server-set; invalid enum → 400.
- [ ] AC-2 — Get returns all fields including created-by/at and modified-at.
- [ ] AC-3 — Editing a field updates `modified_at`; saving unchanged values does NOT advance it (AMB-3).
- [ ] AC-4 — Delete requires the resource to exist (404 otherwise) and removes its comments.
- [ ] AC-5 — Title/body non-empty and within limits; violations → 400 with field errors.

## Test plan
**Unit (JUnit 5 + Mockito):**
- Positive: create/edit/get/delete happy paths; `created_by` from principal.
- Negative: invalid type/state enum; blank title/body; non-existent ticket on get/edit/delete.
- Boundary: title 200/201, body 10000/10001; no-op update leaves `modified_at` unchanged; a single-field change advances it (fixed clock to assert).

**Integration (Testcontainers — Postgres):**
- CRUD round-trip; enum constraint rejected at API; deleting a ticket with comments removes the comment rows (cascade).
- Timestamps stored in UTC ISO-8601.

## How to run / verify
```bash
cd backend && ./mvnw test -Dtest='*Ticket*'
curl -X POST localhost:8080/api/tickets -H 'Content-Type: application/json' \
  -d '{"teamId":"<uuid>","type":"bug","state":"new","title":"X","body":"Y"}'
```

## Definition of Done
- [ ] AC-1..AC-5 met
- [ ] Unit tests (positive/negative/boundary incl. modified-at no-op) pass
- [ ] Testcontainers integration (enum constraints + comment cascade + UTC) passes
- [ ] INDEX.md status updated
