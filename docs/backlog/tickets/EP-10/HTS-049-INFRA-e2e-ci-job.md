# HTS-049 — [INFRA] Wire the Playwright E2E suite into CI

| Field | Value |
|-------|-------|
| **ID** | HTS-049 |
| **Type** | INFRA |
| **Epic** | EP-10 Improvements & Tech Debt |
| **Story** | ST-05 E2E (should-have) |
| **Status** | DONE |
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
- [x] AC-1 — CI has an `e2e` job triggered on push/PR to `main` (shares the workflow triggers).
- [x] AC-2 — The job builds + starts the compose stack and polls `/api/health` + the SPA before
  testing (readiness gate, not a fixed sleep).
- [x] AC-3 — The job runs the full Playwright suite (`npm run e2e`); a spec failure fails the job.
- [x] AC-4 — The Playwright HTML report is uploaded as a build artifact (`if: always()`).

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
- [x] `e2e` job added to `.github/workflows/ci.yml` (health-gated, artifact upload, teardown)
- [x] Workflow YAML validated (parses via `js-yaml`; 3 jobs `backend`/`frontend`/`e2e`; 11 e2e steps)
- [x] README "Running the tests" notes E2E runs in CI; INDEX.md updated

## Implementation notes (as built)
- **`.github/workflows/ci.yml`** gains a third job `e2e` (ubuntu-latest, parallel with the others):
  checkout → `cp .env.example .env` → `docker compose up --build -d` → wait-for-ready (poll
  `http://localhost:8080/api/health` for `UP`, then the SPA on :8081, `timeout`-bounded, no blind
  sleeps) → `setup-node@v4` (Node 22, npm cache) → `npm ci` → `npx playwright install --with-deps
  chromium` → `npm run e2e` → dump `docker compose logs` on failure → upload `frontend/playwright-report`
  (`if: always()`) → `docker compose down -v`.
- No Playwright config change needed: it already sets `retries: 1` and `forbidOnly` under `CI`.
- On GitHub's ubuntu runners `docker compose up --build` works out of the box (no BuildKit/podman
  hang — that caveat is specific to the local Windows/podman path), so CI uses the plain `--build`
  flow rather than the local `podman build` + `--no-build` path.
- **Validation:** the local suite is green against the composed stack (6/6, HTS-036 + HTS-048) and
  the workflow mirrors those exact commands; YAML parsed clean with `js-yaml`. The job itself runs
  on the next push/PR to `main`.
