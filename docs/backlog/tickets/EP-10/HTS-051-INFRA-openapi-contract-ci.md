# HTS-051 — [INFRA] CI job: OpenAPI contract / generated-types drift check

| Field | Value |
|-------|-------|
| **ID** | HTS-051 |
| **Type** | INFRA |
| **Epic** | EP-10 Improvements & Tech Debt |
| **Story** | ST-06 API contract & docs |
| **Status** | DONE |
| **Depends on** | HTS-050, HTS-049 |
| **Blocks** | — |
| **Priority** | Should-have (non-blocking) |
| **Traceability** | NFR-3, NFR-6; architecture.md §8, §11 |

## Goal
Fail CI when the committed frontend types (`frontend/src/api/schema.d.ts`) no longer match the
backend's live OpenAPI spec — i.e. someone changed the API but didn't regenerate/commit the types,
or changed the types out of step with the code. Automates the drift guardrail HTS-050 introduced.

## Why
`schema.d.ts` is committed so builds don't need a running backend. That convenience means the file
can go stale. Locally the guardrail is "run `npm run gen:api` and `npm run typecheck`"; CI should
enforce it so drift can't merge.

## Scope
- In scope: a dedicated `api-contract` job in `.github/workflows/ci.yml` that boots **only the
  backend** (+ its db/mailpit deps), regenerates the types from the live `/v3/api-docs`, and fails
  if the result differs from the committed file or doesn't typecheck.
- Out of scope: publishing the spec as an artifact (optional); contract-first codegen.

## Technical approach
- New job (ubuntu-latest), parallel with the others:
  1. `cp .env.example .env`
  2. `docker compose up --build -d backend` (starts backend + db + mailpit; not the frontend).
  3. Wait for `http://localhost:8080/api/health` = UP.
  4. `actions/setup-node` (Node 22, npm cache) + `npm ci` in `frontend`.
  5. `npm run gen:api` — regenerates `src/api/schema.d.ts` from the live spec.
  6. `git diff --exit-code -- src/api/schema.d.ts` — fails (with a fix hint) if it changed, meaning
     the committed types are stale.
  7. `npm run typecheck` — fails if the (freshly regenerated) contract no longer matches the SPA code.
  8. Dump `docker compose logs` on failure; `docker compose down -v` at the end.

## Acceptance criteria
- [x] AC-1 — CI has an `api-contract` job on push/PR to `main`.
- [x] AC-2 — It boots the backend and regenerates types from the live `/v3/api-docs`.
- [x] AC-3 — A stale committed `schema.d.ts` fails the job with an actionable message.
- [x] AC-4 — A contract change the SPA hasn't adapted to fails via `typecheck`.

## Test plan
- **Positive:** with an in-sync repo the job is green (regeneration is a no-op diff; typecheck passes).
- **Negative:** editing a backend DTO without regenerating → `git diff` fails; using a removed field
  in the SPA → `typecheck` fails.
- Validated locally: `npm run gen:api` against the live backend reproduces the committed file
  byte-for-byte (HTS-050), so the diff step is a no-op on a clean tree.

## Definition of Done
- [x] `api-contract` job added (backend-only boot, health gate, gen → diff → typecheck, teardown)
- [x] Workflow YAML validated (parses; 4 jobs)
- [x] README/architecture note that drift is enforced in CI; INDEX.md updated

## Implementation notes (as built)
- `.github/workflows/ci.yml` gains `api-contract`: `docker compose up --build -d backend` boots
  backend + db + mailpit (frontend is not a dependency, so it stays down — lighter than the `e2e`
  job, no chromium). After health, `npm ci` → `npm run gen:api` → `git diff --exit-code` (with a
  `::error::` hint to run `gen:api` and commit) → `npm run typecheck`.
- Chosen as a **dedicated** job rather than folding into `e2e`: it's cheaper (no frontend build /
  Playwright / browser download) and reads clearly as "the API contract is in sync". It runs in
  parallel, so it doesn't lengthen the critical path.
