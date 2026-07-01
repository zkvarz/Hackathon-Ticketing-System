# HTS-035 — [QA] Fresh-DB/no-seed verification + DoD acceptance checklist run-through

| Field | Value |
|-------|-------|
| **ID** | HTS-035 |
| **Type** | QA |
| **Epic** | EP-08 Cross-cutting & Delivery |
| **Story** | ST-04 Delivery acceptance |
| **Status** | IN-REVIEW |
| **Depends on** | HTS-016, HTS-018, HTS-020, HTS-024, HTS-028, HTS-030, HTS-033, HTS-034 |
| **Blocks** | HTS-036 |
| **Traceability** | DoD-1..DoD-10; FR-P9; architecture.md §7, §13 |

## Goal
Validate the whole system against the Definition of Done: a fresh database is empty, and every
DoD gate (DoD-1..DoD-10) passes through the running application.

## Scope
- In scope: a documented acceptance checklist mapping each DoD item to concrete steps/evidence;
  a fresh-DB verification (`docker compose down -v && up` → schema + migration metadata only,
  no app data, FR-P9); confirmation that QA can create all data via UI/API (DoD-10).
- Out of scope: automated E2E (HTS-036, complementary).

## Technical approach
- Step-by-step manual (or scripted) run-through of DoD-1..10 with pass/fail evidence.
- DB inspection after a clean boot to confirm zero application rows.
- Record results in this ticket / a checklist doc.

## Acceptance criteria
- [~] AC-1 — Fresh DB after `down -v && up` contains only schema + Flyway metadata (no app rows).
  **Verified statically** (no seed data exists); live SQL confirmation pending a fresh boot.
- [~] AC-2 — Each DoD-1..DoD-10 item exercised. DoD-8/DoD-9 verified statically; DoD-1 also
  verified live via the E2E suite (HTS-036); the rest have a documented run-through ready.
- [x] AC-3 — All data is creatable via UI/API (the walk-through uses no manual DB edits, by design).
- [x] AC-4 — Defect-filing process defined in the checklist (no defects found in static/authoring
  pass).

## Test plan
This ticket *is* the acceptance pass:
- **Positive:** full DoD walk-through green.
- **Negative:** seeded/leftover data would fail AC-1 → must be empty.
- **Boundary:** verify with both an empty system and after creating sample data, then a clean reset returns to empty.

## How to run / verify
```bash
docker compose down -v && docker compose up --build
# then walk DoD-1..10 via the app; inspect DB for emptiness
```

## Definition of Done
- [x] Acceptance checklist authored: [`docs/qa/dod-acceptance-checklist.md`](../../../qa/dod-acceptance-checklist.md)
- [x] Static gates verified from source: DoD-8 (no committed secret) + DoD-9/FR-P9 (no seed data)
- [ ] Live DoD-1..DoD-10 walk-through ticked with evidence — pending a fresh `docker compose up --build`
- [ ] Fresh-DB emptiness confirmed via the documented SQL on a clean boot
- [x] INDEX.md status updated (IN-REVIEW)

## Implementation notes (as built)
- Deliverable: **`docs/qa/dod-acceptance-checklist.md`** — a DoD-1..DoD-10 acceptance matrix with
  per-gate steps, expected evidence, and the automated coverage already backing each item, plus the
  fresh-DB / no-seed verification procedure (exact `psql` row-count SQL, FR-P9) and an AC-4
  defect-filing convention.
- **Statically verified now (no running app needed):**
  - *DoD-9 / FR-P9 (no seed):* the Flyway set V1..V12 is pure DDL — no `INSERT INTO` in any
    migration, and no `CommandLineRunner`/`ApplicationRunner`/`@PostConstruct` loader, `data.sql`, or
    `defer-datasource-initialization` in the backend. A fresh DB therefore holds only the schema +
    `flyway_schema_history`.
  - *DoD-8 (no secret):* `application.yml` sources datasource/SMTP creds from env with placeholder
    defaults only; `.env` is gitignored (`!.env.example` kept); passwords are Argon2id-hashed.
- **Requires the operator (documented, not run here):** the live DoD-1..10 walk-through and the
  fresh-DB SQL both need the stack rebuilt from current source (`docker compose down -v && up
  --build`). The stack running in this environment is a stale pre-HTS-020 build and is not managed
  by this session's container engine, so it was not reset/rebuilt. DoD-1 has already been shown green
  end-to-end by the HTS-036 E2E suite against the live backend.
- Marked **IN-REVIEW**: checklist + static gates done; live sign-off is the operator's fresh-stack pass.
