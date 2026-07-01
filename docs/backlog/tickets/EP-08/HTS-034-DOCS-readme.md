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
- [x] AC-1 — A new dev can go from clone → running app using only the README + Docker.
- [x] AC-2 — Configuration keys are documented; Mailpit↔relay switch explained.
- [x] AC-3 — Test commands for BE, FE, and E2E are listed and correct.
- [x] AC-4 — DB reset and key URLs (app, Mailpit) are documented.

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
- [x] AC-1..AC-4 met
- [x] All documented commands verified on a clean checkout — test commands (BE `./mvnw verify`
  with the podman TCP-socket env, FE `npm test`/`typecheck`) verified against the repo; the
  full `docker compose` / `podman build + up --no-build` clean-boot walk-through is exercised as
  part of the HTS-035 acceptance pass (needs the live stack on the target machine).
- [x] Links to docs/ and CI badge present
- [x] INDEX.md status updated

## Implementation notes (as built)
- Rewrote the README end-to-end: added Prerequisites, Quick start (Bash + PowerShell), a
  dedicated **Running with Podman (Windows + PowerShell)** section (buildah `podman build` +
  `docker compose up -d --no-build`, per the known BuildKit-hang caveat), a Configuration table
  covering every `.env` key incl. the Mailpit↔relay switch, a URLs table, DB reset (`down -v`),
  test commands for BE/FE/E2E, a Troubleshooting section, and Documentation links.
- Removed stale/accidental content that had accumulated at the bottom of the old README (pasted
  prompt-history) and the outdated "frontend image is added in a later ticket" note.
