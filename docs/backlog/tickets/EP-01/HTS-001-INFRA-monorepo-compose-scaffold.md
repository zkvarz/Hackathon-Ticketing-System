# HTS-001 — [INFRA] Monorepo + docker-compose scaffold (db, mailpit, backend, frontend) + .env.example

| Field | Value |
|-------|-------|
| **ID** | HTS-001 |
| **Type** | INFRA |
| **Epic** | EP-01 Foundation |
| **Story** | ST-01 Scaffold |
| **Status** | TODO |
| **Depends on** | — |
| **Blocks** | HTS-002, HTS-003, HTS-004, HTS-034 |
| **Traceability** | architecture.md §3, §4, §5; DoD-7, DoD-8, NFR-7 |

## Goal
Create the monorepo skeleton and a root `docker-compose.yml` that wires the four services
(Postgres, Mailpit, backend, frontend) so the whole system can be brought up from a clean
checkout with a single command.

## Scope
- In scope: top-level folders (`backend/`, `frontend/`, `docs/`, `.github/`); root
  `docker-compose.yml` defining `db`, `mailpit`, `backend`, `frontend` with networks,
  volumes, healthchecks, and `depends_on`; `.env.example` with all documented keys; a root
  `.gitignore` that excludes real `.env`, backend/frontend build outputs, and **IDE files —
  specifically IntelliJ IDEA/JetBrains (`.idea/`, `*.iml`)** as well as VS Code/Eclipse and OS
  noise; `AGENTS.md` onboarding pointer (see project root).
- Out of scope: actual backend/frontend app code (HTS-002 / HTS-003); CI (HTS-004).

## Technical approach
- `docker-compose.yml` per architecture.md §4: `db` (`postgres:16`, named volume,
  `pg_isready` healthcheck), `mailpit` (`axllent/mailpit`, ports 8025/1025), `backend`
  (build `./backend`, `depends_on` db healthy + mailpit, env from `.env`), `frontend`
  (build `./frontend`, depends_on backend, nginx on 80→8081).
- `.env.example` lists every key from architecture.md §5 with safe placeholder values; real
  secrets never committed (DoD-8).
- Placeholder Dockerfiles may be stubbed so `config` validates; real images land in
  HTS-002/003.

## Acceptance criteria
- [ ] AC-1 — `docker compose config` validates with no errors.
- [ ] AC-2 — `docker compose up` starts `db` and `mailpit` healthy; Mailpit UI reachable at `http://localhost:8025`.
- [ ] AC-3 — `.env.example` documents every key in architecture.md §5; `.env` is git-ignored.
- [ ] AC-4 — No real secret is present in any committed file (DoD-8).
- [ ] AC-5 — `.gitignore` excludes IntelliJ/JetBrains files (`.idea/`, `*.iml`); `git status` is clean of IDE files, and `git ls-files` lists none.

## Test plan
This is infrastructure; verification is via compose, not unit tests.
- **Smoke:** `docker compose config`; `docker compose up db mailpit` → both healthy.
- **Negative:** with a missing required env var, backend service fails fast with a clear error (verified once HTS-002 lands).
- **Secret check:** `git grep` for placeholder markers confirms no real credentials committed.

## How to run / verify
```bash
cp .env.example .env
docker compose config
docker compose up db mailpit        # expect both healthy; Mailpit UI at :8025
```

## Definition of Done
- [ ] AC-1..AC-4 met
- [ ] `docker compose config` clean; db + mailpit come up healthy
- [ ] `.gitignore` excludes `.env`, build dirs, IDE files
- [ ] INDEX.md status updated
