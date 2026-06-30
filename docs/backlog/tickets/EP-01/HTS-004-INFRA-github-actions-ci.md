# HTS-004 — [INFRA] GitHub Actions CI: build + test BE & FE (Testcontainers)

| Field | Value |
|-------|-------|
| **ID** | HTS-004 |
| **Type** | INFRA |
| **Epic** | EP-01 Foundation |
| **Story** | ST-04 CI |
| **Status** | TODO |
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
- [ ] AC-1 — Workflow triggers on push and pull_request to main.
- [ ] AC-2 — Backend job runs unit + Testcontainers integration tests and passes.
- [ ] AC-3 — Frontend job builds and runs Vitest tests and passes.
- [ ] AC-4 — A deliberately failing test fails the pipeline (gate works).

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
- [ ] AC-1..AC-4 met
- [ ] Both jobs green on a clean push; red on an injected failure (then reverted)
- [ ] Caching configured; README badge added (with HTS-034)
- [ ] INDEX.md status updated
