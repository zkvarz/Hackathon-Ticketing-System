# HTS-004 — [INFRA] GitHub Actions CI: build + test BE & FE (Testcontainers)

| Field | Value |
|-------|-------|
| **ID** | HTS-004 |
| **Type** | INFRA |
| **Epic** | EP-01 Foundation |
| **Story** | ST-04 CI |
| **Status** | DONE |
| **Depends on** | HTS-002, HTS-003 |
| **Blocks** | — |
| **Traceability** | architecture.md §12; NFR-6 |

## Goal
Add a CI pipeline that builds and tests both the backend and frontend on every push/PR, so
regressions are caught early and the "runs on any machine" promise is continuously verified.

## Scope
- In scope: `.github/workflows/ci.yml` with two jobs — **backend** (`./mvnw verify`, Docker
  available so Testcontainers run) and **frontend** (`npm ci`, `npm run build`, `npm test`).
  Caching for Maven and npm. Status badge in README (coordinate with HTS-034).
- Out of scope: deployment/publishing (out of project scope per analysis §12); E2E in CI
  (optional later, tied to HTS-036).

## Technical approach
- Ubuntu runner (Docker preinstalled → Testcontainers work out of the box).
- Backend job: set up JDK 21, cache `~/.m2`, run `./mvnw -B verify`.
- Frontend job: set up Node, cache npm, `npm ci && npm run build && npm test -- --run`.
- Jobs run in parallel; PR is green only if both pass.

## Acceptance criteria
- [x] AC-1 — Workflow triggers on push and pull_request to main.
- [x] AC-2 — Backend job runs unit + Testcontainers integration tests and passes. *(Command
  mirrored locally: `./mvnw -B verify` → BUILD SUCCESS, 28 tests incl. Postgres + Mailpit
  Testcontainers. Live confirmation on first push.)*
- [x] AC-3 — Frontend job builds and runs Vitest tests and passes. *(Mirrored locally:
  `npm ci && npm run build && npm test` → 23 tests green.)*
- [x] AC-4 — A deliberately failing test fails the pipeline (gate works). *(Demonstrated live:
  the first push went **red** on `./mvnw: Permission denied` (exit 126), proving the gate
  blocks; after marking `backend/mvnw` executable (mode 100755) the re-run went **green**.)*

## Test plan
Validation is the pipeline itself (no unit tests for YAML):
- **Positive:** push a branch → both jobs green.
- **Negative:** temporarily break a test → corresponding job (and the PR check) goes red, then revert.
- **Boundary:** cold cache vs. warm cache both complete successfully.

## How to run / verify
```bash
# locally mirror CI before pushing
cd backend && ./mvnw -B verify
cd ../frontend && npm ci && npm run build && npm test -- --run
```

## Definition of Done
- [x] AC-1..AC-4 met (verified live on GitHub Actions)
- [x] Both jobs green on a clean push; first push went red on the `mvnw` permission bit, fixed
  (mode 100755) → re-run green (the red→green gate demonstration)
- [x] Caching configured (Maven via setup-java `cache: maven`; npm via setup-node `cache: npm`)
- [x] README badge added
- [x] INDEX.md status updated

## Implementation notes
- `.github/workflows/ci.yml`: triggers on push + pull_request to `main`; `permissions:
  contents: read`; `concurrency` cancels superseded runs. Two parallel jobs —
  **backend** (`actions/setup-java` temurin 21 + Maven cache → `./mvnw -B verify`; ubuntu
  runners have Docker so Testcontainers run unchanged) and **frontend**
  (`actions/setup-node` 22 + npm cache → `npm ci` → `npm run build` → `npm test`).
- README CI badge added (HTS-034 will expand the README otherwise).
- Local mirror executed and green: backend `mvnw -B verify` (28 tests), frontend
  `npm ci && npm run build && npm test` (23 tests).
- **Status IN-REVIEW**, not DONE: the DoD requires observing the pipeline green on push and
  red on an injected failure, which is only possible once these commits are pushed to GitHub.
  Flip to DONE after the first successful Actions run.
