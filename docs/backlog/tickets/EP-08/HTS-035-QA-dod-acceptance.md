# HTS-035 — [QA] Fresh-DB/no-seed verification + DoD acceptance checklist run-through

| Field | Value |
|-------|-------|
| **ID** | HTS-035 |
| **Type** | QA |
| **Epic** | EP-08 Cross-cutting & Delivery |
| **Story** | ST-04 Delivery acceptance |
| **Status** | TODO |
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
- [ ] AC-1 — Fresh DB after `down -v && up` contains only schema + Flyway metadata (no app rows).
- [ ] AC-2 — Each DoD-1..DoD-10 item is exercised and passes, with evidence noted.
- [ ] AC-3 — All required test/demo data is creatable via UI/API (no manual DB edits) (DoD-10).
- [ ] AC-4 — Any failure is filed as a defect ticket and linked.

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
- [ ] AC-1..AC-4 met
- [ ] DoD-1..DoD-10 checklist completed with evidence
- [ ] Fresh-DB emptiness confirmed
- [ ] INDEX.md status updated
