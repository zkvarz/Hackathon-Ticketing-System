# Hackathon-Ticketing-System

[![CI](https://github.com/zkvarz/Hackathon-Ticketing-System/actions/workflows/ci.yml/badge.svg)](https://github.com/zkvarz/Hackathon-Ticketing-System/actions/workflows/ci.yml)

A small, working Kanban-style ticket tracker: a three-tier SPA (React) + REST API (Spring Boot)
+ PostgreSQL, with email verification captured by a local Mailpit inbox. Everything runs from a
single `docker compose up --build` — no host-installed runtimes required beyond a container engine.

> **Developers & AI agents:** start with [`AGENTS.md`](AGENTS.md) — it points to the spec,
> analysis, architecture, and the ticket backlog, and explains the spec-driven workflow.

## Contents

- [Quick start](#quick-start) · [Running with Podman](#running-with-podman-windows--powershell)
- [Prerequisites](#prerequisites) · [Configuration](#configuration) · [URLs](#urls)
- [Resetting the database](#resetting-the-database) · [Running the tests](#running-the-tests)
- [Troubleshooting](#troubleshooting) · [Documentation](#documentation)
- [Requirements provenance](#requirements-provenance) · [Tech stack](#tech-stack--architecture-decisions)

## Prerequisites

The only hard requirement is a working **container engine + Compose**:

- **Docker Desktop** (Docker Engine 24+ and the `docker compose` v2 plugin), **or**
- **Podman** 4+ with `podman compose` / a `docker`-compatible CLI (see the Podman section below).

That's it — the JDK, Node, Maven, and Postgres all run inside containers. To run the test suites
or the frontend dev server directly on the host (optional), you'd additionally want **JDK 21** and
**Node 22+**, but neither is needed just to start the app.

## Quick start

```bash
# from a clean clone (Linux/macOS or Git Bash)
cp .env.example .env
docker compose up --build
```

Then open **http://localhost:8081**. First run pulls base images and builds both apps, so give it
a few minutes; subsequent starts are fast. The backend waits for Postgres to be healthy and runs
its Flyway migrations on boot, so the schema is created automatically against an empty database.

On **Windows PowerShell** the only difference is the copy command:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

## Running with Podman (Windows + PowerShell)

The project targets standard Docker/Docker Compose, but it also runs under **Podman**. One
caveat on some Podman setups (including this one): `docker compose build` /
`docker compose up --build` routes image builds through a `buildx_buildkit_default` BuildKit
container whose session can **hang** for minutes. The fix is to build the images with Podman's
native builder (buildah, no BuildKit) and then start the stack **without rebuilding**.

```powershell
# 0) One-time: create the env file
Copy-Item .env.example .env

# 1) Build both images with podman (buildah — does NOT use BuildKit)
podman build -t hts-backend:dev ./backend
podman build -t hts-frontend:dev ./frontend

# 2) Start the whole stack WITHOUT rebuilding (uses the images built above).
#    --no-build is the important flag on this path — never `--build`.
docker compose up -d --no-build

# 3) Watch it come up / tail logs
docker compose ps
docker compose logs -f backend
```

> The Compose file tags the images `hts-backend:dev` and `hts-frontend:dev`, which is why the
> `podman build -t …` tags above line up with what `docker compose up --no-build` expects. If
> `docker compose` isn't wired to your podman socket, the equivalent `podman compose up -d
> --no-build` works the same way.

Open **http://localhost:8081** once `docker compose ps` shows the `frontend` and `backend`
services healthy/up.

## Configuration

All configuration comes from a `.env` file at the repo root — copy it from
[`.env.example`](.env.example) and adjust as needed. **Never commit `.env`** (it's gitignored).
Full rationale is in [`docs/architecture.md`](docs/architecture.md) §5.

| Key | Default | Purpose |
|-----|---------|---------|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `tickets` / `tickets` / `change-me` | Postgres container credentials. |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://db:5432/tickets` | Backend → DB (uses the compose service name `db`). |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | `tickets` / `change-me` | Must match the `POSTGRES_*` values above. |
| `APP_SESSION_TIMEOUT_IDLE` | `30m` | Idle/sliding session timeout (AMB-7 / A-3). |
| `APP_SESSION_TIMEOUT_ABSOLUTE` | `8h` | Absolute max session lifetime (enforced by HTS-046). |
| `APP_VERIFICATION_TOKEN_TTL` | `24h` | Email verification token lifetime (FR-A8). |
| `APP_BASE_URL` | `http://localhost:8081` | Base URL used to build verification links in emails (FR-A6). |
| `SMTP_HOST` / `SMTP_PORT` | `mailpit` / `1025` | SMTP target. Defaults point at the dockerized Mailpit. |
| `SMTP_USERNAME` / `SMTP_PASSWORD` | *(empty)* | SMTP auth (unused with Mailpit). |
| `SMTP_FROM` | `no-reply@tickets.local` | From-address on outgoing mail. |

**Mailpit vs. a real relay.** By default all outgoing mail is captured by the dockerized
**Mailpit** service — no mail leaves your machine, and you read verification links from the
Mailpit UI (below). To send through the real relay instead, point the SMTP variables at it — e.g.
`SMTP_HOST=relay1.dataart.com` plus the appropriate port/credentials — and restart. This is a
config-only switch; no code changes are required.

## URLs

| Service | URL | Notes |
|---------|-----|-------|
| **App (SPA)** | http://localhost:8081 | nginx serves the SPA and reverse-proxies `/api` → backend (same-origin). |
| Backend API | http://localhost:8080 | Health check: `GET /api/health`. Normally reached via the SPA proxy. |
| **Mailpit inbox** | http://localhost:8025 | Read verification emails here; also has an HTTP API used by the E2E suite. |
| Postgres | `localhost:5432` | Credentials from `.env`. |

## Resetting the database

The Postgres data lives in the `db-data` named volume, so it **survives** `docker compose down`.
To wipe it and get a truly fresh, empty database (schema + Flyway metadata only), remove the
volume with `-v`:

```powershell
# Full reset to an empty DB, then rebuild/start
docker compose down -v
docker compose up --build          # or, on podman: docker compose up -d --no-build
```

## Running the tests

Each side has its own suite; you don't need the full stack up to run them.

**Backend — unit (Mockito) + integration (Testcontainers).** Testcontainers needs a running
container engine (Docker or Podman) reachable via the Docker API:

```bash
cd backend
./mvnw verify                       # unit + integration; or ./mvnw test for unit-focused runs
```

> On this machine the Docker daemon hangs, so integration tests run against **Podman** via a
> Docker-compatible TCP socket. In PowerShell:
> ```powershell
> $env:DOCKER_HOST = "tcp://localhost:2375"
> $env:TESTCONTAINERS_RYUK_DISABLED = "true"
> cd backend; ./mvnw -B verify "-Duser.timezone=UTC"
> ```

**Frontend — component (Vitest + RTL) + API-contract (MSW):**

```bash
cd frontend
npm ci          # first time only
npm test        # vitest run
npm run typecheck
```

**End-to-end — Playwright critical paths (HTS-036, should-have).** Six specs run against the
composed stack in a real browser: signup → verify (read from Mailpit's HTTP API) → login (DoD-1),
ticket create/edit/delete (DoD-3), drag-persists-after-refresh (DoD-6), epic create/edit/delete +
referenced-guard (DoD-2), and a team delete-when-referenced guard. A `setup` project
registers/verifies/logs in one shared user; each spec creates its own team/ticket. **This suite
also runs in CI** — the `e2e` job (HTS-049) boots the compose stack and runs it on every push/PR to
`main`, uploading the HTML report as a build artifact.

```bash
# The stack MUST be built from current source — start it fresh so the containers aren't stale:
docker compose up --build -d        # or the podman build + --no-build path above
cd frontend
npm ci                              # first time only
npx playwright install chromium     # first time only (downloads the browser)
npm run e2e                         # = playwright test  (npm run e2e:report to open the HTML report)
```

Targets default to `http://localhost:8081` (app) and `http://localhost:8025` (Mailpit); override
with `PW_BASE_URL` / `MAILPIT_URL` if your ports differ. The suite assumes the stack is already up —
it does not start it.

## Troubleshooting

- **Build hangs for minutes on Podman** → you hit the BuildKit caveat. Stop it, and use the
  [Running with Podman](#running-with-podman-windows--powershell) path (`podman build` + `up --no-build`).
- **Backend exits / can't connect to the DB** → the datasource in `.env` must match the
  `POSTGRES_*` credentials, and `SPRING_DATASOURCE_URL` must use host `db` (the compose service
  name), not `localhost`.
- **No verification email arrives** → with the defaults, mail goes to Mailpit, not a real inbox —
  open http://localhost:8025 to read it.
- **"port already allocated"** → something else is using 8081/8080/8025/5432; stop it or change the
  host-side port mappings in `docker-compose.yml`.
- **Missing `.env`** → Compose interpolates `${…}` from `.env`; if you skipped the copy step you'll
  see empty-variable warnings and the DB/back end won't get credentials. Copy `.env.example` first.

## Documentation

- [`AGENTS.md`](AGENTS.md) — workflow + entry point for developers/AI agents.
- [`docs/requirements.md`](docs/requirements.md) — original requirements (converted to Markdown).
- [`docs/requirements-analysis.md`](docs/requirements-analysis.md) — functional/non-functional
  requirements, ambiguities, assumptions, decisions.
- [`docs/architecture.md`](docs/architecture.md) — architecture, folder structure, data model,
  config, security.
- [`docs/backlog/INDEX.md`](docs/backlog/INDEX.md) — epics, stories, and the implementation-ticket
  inventory with status.

## Requirements provenance

The original requirements were a Word document (`Hackathon_Ticketing_System_Requirements_v3.docx`).
To make them easier to work with using an AI agentic approach (e.g. Claude Code), the document was
converted to Markdown with [pandoc](https://pandoc.org/):

```bash
pandoc Hackathon_Ticketing_System_Requirements_v3.docx -t markdown --extract-media=./media -o requirements.md
```

This produced `docs/requirements.md` (the requirements in Markdown) and `docs/media/media/` (images
extracted from the document). The spec-driven analysis derived from them —
functional/non-functional requirements, architecture, and explicitly flagged ambiguities,
assumptions, and open questions — lives in
[`docs/requirements-analysis.md`](docs/requirements-analysis.md).

## Tech stack & architecture decisions

A three-tier application in a single monorepo, with the frontend and backend as separate containers
orchestrated by a root `docker-compose.yml`:

| Tier | Decision |
|------|----------|
| **Backend** | Spring Boot (Java 21) — Spring Security (Argon2id password hashing, endpoint auth, session cookie + CSRF), Spring Data JPA + Flyway migrations, Spring Mail for SMTP, Testcontainers for integration tests. |
| **Frontend** | TypeScript + React SPA (Vite), TanStack Query for data, `@dnd-kit` for the Kanban drag-and-drop. |
| **Database** | PostgreSQL 16, in its own container. |
| **Email (dev/test)** | Dockerized [Mailpit](https://github.com/axllent/mailpit) captures verification emails locally; the real `relay1.dataart.com` relay is selectable via SMTP environment variables (config-only switch, no code change). |
| **Packaging** | `docker compose up --build` from the repo root brings up database, Mailpit, backend, and frontend. No host-installed runtimes required beyond a container engine. |

Repository layout:

```
/ (repo root)
├── docker-compose.yml        # orchestrates db, mailpit, backend, frontend
├── .env.example              # documented secrets/config keys
├── backend/                  # Spring Boot app + its Dockerfile
├── frontend/                 # React + TypeScript SPA + its Dockerfile
├── docs/                     # requirements, analysis, architecture, backlog
└── README.md
```

See [`docs/requirements-analysis.md`](docs/requirements-analysis.md) §4.3 for the full decision
rationale.
