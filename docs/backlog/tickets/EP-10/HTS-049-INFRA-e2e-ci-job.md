# HTS-049 — [INFRA] Wire the Playwright E2E suite into CI

| Field | Value |
|-------|-------|
| **ID** | HTS-049 |
| **Type** | INFRA |
| **Epic** | EP-10 Improvements & Tech Debt |
| **Story** | ST-05 E2E (should-have) |
| **Status** | TODO |
| **Depends on** | HTS-036 |
| **Blocks** | — |
| **Priority** | Should-have (non-blocking) |
| **Traceability** | NFR-6; DoD-1, DoD-3, DoD-6; architecture.md §12 |

## Goal
Run the Playwright critical-path suite automatically in GitHub Actions against a full, freshly
built `docker compose` stack, so signup→verify→login (DoD-1), ticket CRUD (DoD-3), and
drag-persists (DoD-6) are guarded on every push/PR — not just locally.

## Why
HTS-036 authored the suite but left CI as an explicit follow-up ("Out of scope: running in CI").
GitHub's ubuntu runners ship Docker + Compose (no BuildKit/podman hang), so the stack that is
awkward to build locally builds cleanly there — the ideal place to run E2E continuously.

## Scope
- In scope: a new `e2e` job in `.github/workflows/ci.yml` that boots the compose stack, waits for
  health, installs Playwright + chromium, runs the suite, and uploads the HTML report as an
  artifact. Runs on the same push/PR-to-main triggers as the other jobs.
- Out of scope: sharding/matrix, cross-browser runs, visual regression, deploy gating.

## Technical approach
- New job `e2e` (ubuntu-latest), independent of the `backend`/`frontend` jobs (parallel):
  1. `cp .env.example .env`
  2. `docker compose up --build -d` (Docker on the runner — no `--no-build`/podman path needed)
  3. Wait for `http://localhost:8080/api/health` to return `{"status":"UP"}` and the SPA on :8081.
  4. `actions/setup-node` (Node 22, npm cache), `npm ci` in `frontend`.
  5. `npx playwright install --with-deps chromium`.
  6. `npm run e2e` (targets default to :8081 / :8025).
  7. `actions/upload-artifact` for `frontend/playwright-report` (`if: always()`); dump
     `docker compose logs` on failure; `docker compose down -v` at the end.
- Playwright config already sets `retries: 1` and `forbidOnly` under `CI`, so no config change is
  needed.

## Acceptance criteria
- [ ] AC-1 — CI has an `e2e` job triggered on push/PR to `main`.
- [ ] AC-2 — The job builds + starts the compose stack and waits for backend/frontend health before
  testing (no arbitrary sleeps as the readiness gate).
- [ ] AC-3 — The job runs the full Playwright suite; a spec failure fails the job.
- [ ] AC-4 — The Playwright HTML report is uploaded as a build artifact (on pass and fail).

## Test plan
- **Positive:** on a healthy stack the job runs all specs green.
- **Negative:** a deliberately failing spec (or a down service) fails the job and still uploads the
  report for triage.
- Validated locally that the suite is green against the composed stack (HTS-036); the workflow
  mirrors those exact commands.

## How to run / verify
- Push to a branch / open a PR and observe the `e2e` job in the Actions tab.
- Locally, the same sequence: `docker compose up --build -d` then `cd frontend && npm run e2e`.

## Definition of Done
- [ ] `e2e` job added to `.github/workflows/ci.yml` (health-gated, artifact upload, teardown)
- [ ] Workflow YAML validated (parses; steps ordered; working directories correct)
- [ ] README "Running the tests" notes E2E runs in CI; INDEX.md updated

## Implementation notes (as built)
_(filled on completion)_
