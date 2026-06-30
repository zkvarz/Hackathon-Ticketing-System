# EP-01 — Foundation

**Goal.** Stand up the monorepo, container orchestration, and the backend/frontend baselines
so that `docker compose up --build` runs an empty-but-wired three-tier app from a clean
checkout, with CI building and testing both sides.

**Why first.** Every other epic depends on a runnable skeleton, a connected database with
migrations, an SPA shell, and a test harness. This epic delivers no business features — it
delivers the platform they sit on (DoD-7, DoD-8).

**Architecture references:** §3 (folder structure), §4 (containers/compose), §5 (config),
§7 (migrations), §11 (frontend baseline), §12 (testing conventions).

## Stories & tickets

| Story | Ticket | Type | Title |
|-------|--------|------|-------|
| ST-01 Scaffold | HTS-001 | INFRA | Monorepo + docker-compose scaffold + .env.example |
| ST-02 Backend baseline | HTS-002 | BE | Spring Boot baseline: config, Postgres, Flyway, health, Dockerfile |
| ST-03 Frontend baseline | HTS-003 | FE | Vite + React + TS baseline: router shell, API client, layout, Dockerfile |
| ST-04 CI | HTS-004 | INFRA | GitHub Actions CI: build + test BE & FE |

> Note: HTS-001 and HTS-004 are infrastructure tickets that span both tiers, so they are not
> split BE/FE. The BE/FE split rule applies to feature stories (EP-02 onward). HTS-002/003
> are the per-tier baselines.

**Exit criteria for the epic:**
- `docker compose up --build` starts db + mailpit + backend + frontend with no errors.
- Backend `/api/health` returns 200; Flyway baseline migration applied; DB empty of app data.
- Frontend shell loads in the browser and can call the backend health endpoint.
- CI runs BE + FE test suites green on push.
