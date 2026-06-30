# HTS-034 — [DOCS] README: prerequisites, configuration, startup, run/test commands

| Field | Value |
|-------|-------|
| **ID** | HTS-034 |
| **Type** | DOCS |
| **Epic** | EP-08 Cross-cutting & Delivery |
| **Story** | ST-03 Docs |
| **Status** | TODO |
| **Depends on** | HTS-001 |
| **Blocks** | HTS-035 |
| **Traceability** | NFR-5; DoD-7; architecture.md §13 |

## Goal
Write the README so a fresh developer/QA can configure, start, and test the whole system on a
clean machine with only Docker installed.

## Scope
- In scope: prerequisites (Docker/Docker Compose only — NFR-7); configuration (`.env` from
  `.env.example`, key explanations incl. Mailpit vs real relay); startup (`docker compose up
  --build`); URLs (app, Mailpit UI); how to reset the DB; how to run BE/FE/E2E tests; CI badge
  (HTS-004); links to `docs/` (requirements, analysis, architecture, backlog).
- Out of scope: deployment guides (out of scope §12).

## Technical approach
- Extend the existing README; keep the requirements/decisions sections already present.
- Verify every command by running it on a clean checkout before marking done.

## Acceptance criteria
- [ ] AC-1 — A new dev can go from clone → running app using only the README + Docker.
- [ ] AC-2 — Configuration keys are documented; Mailpit↔relay switch explained.
- [ ] AC-3 — Test commands for BE, FE, and E2E are listed and correct.
- [ ] AC-4 — DB reset and key URLs (app, Mailpit) are documented.

## Test plan
Doc verification (not unit tests):
- **Positive:** follow the README on a clean checkout/clone → app comes up; tests run.
- **Negative:** intentionally skip the `.env` step → documented error/troubleshooting note matches reality.
- **Boundary:** verify on at least one OS (and note cross-platform expectations per NFR-7).

## How to run / verify
```bash
# from a clean clone
cp .env.example .env && docker compose up --build
```

## Definition of Done
- [ ] AC-1..AC-4 met
- [ ] All documented commands verified on a clean checkout
- [ ] Links to docs/ and CI badge present
- [ ] INDEX.md status updated
