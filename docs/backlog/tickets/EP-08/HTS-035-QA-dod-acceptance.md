# HTS-035 — [QA] Fresh-DB/no-seed verification + DoD acceptance checklist run-through

| Field | Value |
|-------|-------|
| **ID** | HTS-035 |
| **Type** | QA |
| **Epic** | EP-08 Cross-cutting & Delivery |
| **Story** | ST-04 Delivery acceptance |
| **Status** | DONE |
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
- [x] AC-1 — Fresh DB after `down -v && up` contains only schema + Flyway metadata (no app rows).
  **Confirmed live (2026-07-01):** all 8 app tables = 0 rows; `flyway_schema_history` = 12 migrations.
- [x] AC-2 — Each DoD-1..DoD-10 item exercised live on the freshly built stack (E2E 5/5 green +
  fresh-DB SQL + authenticated API smoke). See the checklist matrix.
- [x] AC-3 — All data creatable via UI/API; the run-through used no manual DB edits (only read-only
  count queries for AC-1).
- [x] AC-4 — Defect-filing process defined; **no defects found** in the live run-through.

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
- [x] Live DoD-1..DoD-10 walk-through ticked with evidence (2026-07-01, commit `956d100`)
- [x] Fresh-DB emptiness confirmed via the documented SQL on a clean boot
- [x] INDEX.md status updated (DONE)

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
- **Live sign-off completed (2026-07-01):** the operator rebuilt both images and brought the stack
  up on a fresh DB volume. During finalization we found the compose `frontend` service lacked an
  `image:` tag, so a stale auto-built frontend image was being reused; pinned `image: hts-frontend:dev`
  (mirrors `backend`) and recreated the container so the freshly built assets are served.
  - *Fresh-DB (AC-1/DoD-9):* 8 app tables = 0 rows; `flyway_schema_history` = 12 migrations.
  - *E2E (DoD-1/3/6 + teams):* `npx playwright test` → **5/5 green**.
  - *API smoke (DoD-2/4/5/6/10):* signup→verify→login→CSRF→team+ticket→comment (author+timestamp)→
    state change (persisted)→activity log — all through the app, no manual DB edits.
- Marked **DONE**: checklist ticked with evidence; no defects.
