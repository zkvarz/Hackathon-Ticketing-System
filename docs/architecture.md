# Architecture — Hackathon Ticketing System

> **Purpose.** This is the single source of structural truth for the project. The backlog
> tickets (`docs/backlog/`) reference sections here instead of repeating shared detail.
> All decisions trace back to `docs/requirements.md` (spec) and `docs/requirements-analysis.md`
> (analysis with FR/NFR/DoD/AMB IDs).
>
> **If this document and a ticket disagree, this document wins** — update the ticket.

---

## 1. System context

Three clearly separated logical tiers (FR §2 / Architecture §4.1 of the analysis):

```
┌──────────────┐     HTTPS/JSON      ┌────────────────┐     JDBC      ┌──────────────┐
│  Browser SPA │  ───────────────▶   │  Backend API   │  ──────────▶  │  PostgreSQL  │
│ React + TS   │  ◀───────────────   │  Spring Boot   │  ◀──────────  │   (RDBMS)    │
│ (nginx)      │   session cookie    │  (REST)        │               │              │
└──────────────┘                     └───────┬────────┘               └──────────────┘
                                              │ SMTP
                                              ▼
                                      ┌────────────────┐
                                      │    Mailpit      │  (dev/test SMTP sink + web UI)
                                      └────────────────┘
```

- **Presentation:** React + TypeScript SPA, served as a static build by nginx in its own container.
- **Application:** Spring Boot REST API; owns all business rules and validation (FR-K8, FR-P3).
- **Persistence:** PostgreSQL in its own container; the only system of record (FR-P1, FR-P2).
- **Email:** Mailpit in dev/test (A-4); swappable for `relay1.dataart.com` via env vars only.

---

## 2. Technology stack

Decided in `requirements-analysis.md` §4.3. Summary:

| Concern | Choice |
|---------|--------|
| Backend | Spring Boot (Java 21), Spring Web, Spring Security, Spring Data JPA, Spring Mail |
| Migrations | Flyway |
| Database | PostgreSQL 16 |
| IDs | UUIDv7 (A-5) |
| Auth | Server-side session cookies, `HttpOnly`/`Secure`/`SameSite`; 8h absolute + 30 min idle (A-3 / AMB-7) |
| Password hashing | Argon2id (AMB-2) |
| Frontend | React 18 + TypeScript, Vite, React Router, TanStack Query (server state), dnd-kit (board) |
| BE tests | JUnit 5 + Mockito (unit); Testcontainers (Postgres, Mailpit) for integration |
| FE tests | Vitest + React Testing Library (component); MSW (API-contract) |
| E2E | Playwright (small critical-path suite, should-have) |
| Email (dev) | Mailpit |
| Orchestration | Docker Compose (root) |

> Java is the baseline; Kotlin-on-JVM inside Spring Boot is an acceptable substitute and
> changes none of the structure below.

---

## 3. Repository / folder structure

Monorepo, separate BE/FE containers (A-9):

```
/ (repo root)
├── docker-compose.yml            # db, mailpit, backend, frontend
├── .env.example                  # documented config/secret keys (A-7); real .env is git-ignored
├── .gitignore
├── README.md
├── .github/
│   └── workflows/ci.yml          # build + test BE & FE (HTS-004)
├── docs/
│   ├── requirements.md
│   ├── requirements-analysis.md
│   ├── architecture.md           # this file
│   ├── media/media/              # wireframes
│   └── backlog/                  # epics, stories, tickets, INDEX
├── backend/
│   ├── Dockerfile                # multi-stage: build (Maven) → slim JRE runtime
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/dataart/tickets/
│       │   ├── TicketsApplication.java
│       │   ├── config/           # security, CORS, session, mail, OpenAPI
│       │   ├── auth/             # signup, verification, login/logout
│       │   ├── team/             # entity, repo, service, controller, dto
│       │   ├── epic/
│       │   ├── ticket/
│       │   ├── comment/
│       │   ├── board/            # board query + state transition
│       │   └── common/           # error model, validation, base entity, UUIDv7
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── db/migration/     # Flyway V1__*.sql ...
│       └── test/java/...         # mirrors main; unit + integration (Testcontainers)
└── frontend/
    ├── Dockerfile                # multi-stage: build (vite) → nginx serving /dist
    ├── nginx.conf                # SPA fallback + /api proxy (dev) 
    ├── package.json
    ├── vite.config.ts
    └── src/
        ├── main.tsx, App.tsx, router.tsx
        ├── api/                  # typed API client + query hooks
        ├── auth/                 # auth context, route guards
        ├── features/             # teams, epics, tickets, comments, board
        ├── components/           # shared UI (states: loading/empty/error)
        └── test/                 # vitest setup, MSW handlers
```

Per-feature BE package = entity + repository + service + controller + DTOs + mapper.
Per-feature FE folder = components + hooks + types + tests.

---

## 4. Container topology & Docker Compose

`docker compose up --build` from the repo root brings up the whole system (DoD-7). No
host-installed runtimes beyond Docker (NFR-7).

| Service | Image / build | Port (host→container) | Notes |
|---------|---------------|------------------------|-------|
| `db` | `postgres:16` | 5432→5432 | Named volume for data; healthcheck `pg_isready`. |
| `mailpit` | `axllent/mailpit` | 8025→8025 (UI), 1025→1025 (SMTP) | Dev/test mail sink. |
| `backend` | `./backend` | 8080→8080 | `depends_on: db (healthy), mailpit`; runs Flyway on boot. |
| `frontend` | `./frontend` | 8081→80 | nginx serves SPA build; proxies `/api` → `backend`. |

- The frontend talks to the backend at a configurable base URL; in Compose, nginx proxies
  `/api` to `backend:8080` so the browser sees a same-origin app (simplifies cookie/CSRF).
- `db` data lives in a named volume; **no seed data** is loaded (FR-P9, DoD-9). A fresh
  `docker compose down -v && up` yields schema + Flyway metadata only.

---

## 5. Configuration & secrets

All config via environment variables (A-7); `.env.example` documents every key, real `.env`
is git-ignored; **no secret is committed** (NFR-1, DoD-8). Planned keys:

```
# Database
POSTGRES_DB=tickets
POSTGRES_USER=tickets
POSTGRES_PASSWORD=change-me
# Backend
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/tickets
APP_SESSION_TIMEOUT_IDLE=30m
APP_SESSION_TIMEOUT_ABSOLUTE=8h
APP_VERIFICATION_TOKEN_TTL=24h
APP_BASE_URL=http://localhost:8081        # used to build verification links
# SMTP (Mailpit by default; switch to relay1.dataart.com via these vars only)
SMTP_HOST=mailpit
SMTP_PORT=1025
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_FROM=no-reply@tickets.local
```

Switching from Mailpit to the real relay is **config-only** (`SMTP_HOST=relay1.dataart.com`,
port/credentials) — no code change (AMB-6 / A-4).

---

## 6. Data model

Entities derived from spec §§3–7. All tables have UUIDv7 `id` (A-5) and UTC timestamps
(A-6, FR-P5). `created_at`/`modified_at` are server-set and **single-sourced by Spring Data JPA
Auditing** driven by the app `Clock` (HTS-047): `@CreatedDate`/`@LastModifiedDate` on `BaseEntity`
draw from one `DateTimeProvider`, so the persisted instant equals the one the service returns and is
deterministic under a fixed clock. `@LastModifiedDate` fires only when a real field change dirties
the row (AMB-3); the ticket state PATCH forces the row dirty so it advances even on the same state
(HTS-027 exception).

```
USER ──< EMAIL_VERIFICATION_TOKEN
USER ──< TICKET (created_by)
USER ──< COMMENT (author)
TEAM ──< EPIC          (epic.team_id, immutable — FR-E2)
TEAM ──< TICKET        (ticket.team_id)
EPIC ──< TICKET        (ticket.epic_id, nullable; same team as ticket — FR-E7)
TICKET ──< COMMENT     (cascade delete — FR-K6)
```

| Entity | Key fields | Key constraints |
|--------|-----------|-----------------|
| **User** | id, email, password_hash, email_verified (bool), created_at, modified_at | email unique on **lower(trim(email))** (FR-A2); password_hash never plaintext (FR-A5). |
| **EmailVerificationToken** | id, user_id, token, expires_at, consumed_at (null) | single-use (FR-A8); issuing a new one invalidates prior unused (FR-A11). |
| **Team** | id, name, created_at, modified_at | name unique on **lower(trim(name))** (FR-T4); delete blocked if referenced (FR-T5 → 409). |
| **Epic** | id, team_id, title, description (null), created_at, modified_at | title non-empty trimmed (FR-E5); team_id immutable (FR-E2); delete blocked if referenced (FR-E8 → 409). |
| **Ticket** | id, team_id, type, state, epic_id (null), title, body, created_at, modified_at, created_by | type ∈ {bug,feature,fix}; state ∈ 5 values; epic_id same team (FR-E7); modified-at advances only on real change (AMB-3). |
| **Comment** | id, ticket_id, author_id, body, created_at, edited_at (null) | body non-empty (FR-C3); does not bump ticket.modified_at (FR-C5). Immutable by default (FR-C6); the EP-09 stretch (HTS-039) relaxes this so the **author** may edit/delete their own comment — edit stamps `edited_at`, non-author → 403. |

**Field limits (AMB-1, binding):** password ≤128, title ≤200, body ≤10000 chars — enforced
server-side and reflected in column types.

**Enum canonical values (API):** `type = bug | feature | fix`; `state = new |
ready_for_implementation | in_progress | ready_for_acceptance | done`. UI shows
human-readable labels (FR §6).

---

## 7. Database & migrations

- **Flyway** versioned migrations in `backend/src/main/resources/db/migration` (FR-P8).
- Schema auto-applies on backend boot; repeatable and idempotent.
- **Extensions:** `pg_trgm` (ships with the `postgres:16` image) backs the board title
  substring search — a GIN trigram index on `lower(title)` (`ix_tickets_title_trgm`, HTS-044)
  so the `?q=` leading-wildcard `LIKE` uses a bitmap index scan instead of a seq scan (FR-B9/B10).
- **No seed/sample data** on the default path (FR-P9, DoD-9); QA creates data via UI/API
  (DoD-10).
- Referential integrity via FK constraints + server-side checks (FR-P3); delete restrictions
  on Team/Epic surface as HTTP 409 (FR-P4).

---

## 8. API surface

REST/JSON under `/api`. Conventions: plural nouns, UUIDv7 path IDs, ISO-8601 UTC timestamps
(FR-P5), tokens never in URLs except the single-use verification token (FR-P6).

| Area | Endpoints (indicative) | Auth |
|------|------------------------|------|
| Auth | `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me` | public except `me`/`logout` |
| Verification | `GET /api/auth/verify?token=…`, `POST /api/auth/resend` | public |
| Teams | `GET/POST /api/teams`, `GET/PUT/DELETE /api/teams/{id}` | required |
| Epics | `GET/POST /api/epics?teamId=…`, `GET/PUT/DELETE /api/epics/{id}` | required |
| Tickets | `GET/POST /api/tickets?teamId=…&type=&epicId=&q=`, `GET/PUT/DELETE /api/tickets/{id}`, `PATCH /api/tickets/{id}/state` | required |
| Comments | `GET/POST /api/tickets/{id}/comments` | required |
| Board | served via `GET /api/tickets?teamId=…` ordered by modified desc | required |
| Health | `GET /api/health` | public |

**Standardized error model** (HTS-031), returned for all 4xx/5xx (FR-P4):

```json
{ "timestamp": "2026-06-30T10:00:00Z", "status": 409, "error": "Conflict",
  "code": "TEAM_HAS_CHILDREN", "message": "Team has tickets or epics and cannot be deleted.",
  "fieldErrors": [] }
```

Status code map: 400 validation, 401 unauthenticated, 403 forbidden/CSRF, 404 missing,
409 conflict (referenced delete, duplicate name/email), 422 optional for semantic validation.

---

## 9. Authentication & session flow

Server-side session cookies (A-3 / AMB-7), via Spring Security.

- **Sign up** → validate, Argon2id-hash password, persist unverified user, issue verification
  token, send email. (FR-A1, A2, A4, A5; HTS-005/007)
- **Verify** → validate token (exists, not expired ≤24h, not consumed), mark user verified,
  consume token, redirect to login (no auto-login — FR-A9). (HTS-007)
- **Resend** → invalidate prior unused tokens, issue + send new. (FR-A10/A11; HTS-009)
- **Login** → reject unverified (FR-A7), verify password, create server session, set
  `HttpOnly`/`Secure`/`SameSite` cookie. (HTS-011)
- **Logout** → invalidate session server-side (FR-A3). (HTS-011)
- **Enforcement** → all endpoints require auth except signup/login/verify/resend + health +
  static assets (FR-A12). CSRF protection on state-changing requests (Spring Security default,
  SPA reads token from cookie/header). (HTS-013/014)

Session lifetime: 8h absolute + 30 min idle; no "remember me". The **idle/sliding** timeout is
the servlet container's (`server.servlet.session.timeout`); the **absolute** cap
(`app.session.absolute-timeout`) is enforced by `SessionAbsoluteTimeoutFilter` (HTS-046), which
stamps the login instant on the session and, once `now − createdAt ≥ cap`, invalidates it and
returns the standard `401 UNAUTHENTICATED` — independent of activity. The filter is clock-driven
(injected `Clock`) for deterministic tests.

---

## 10. Email verification flow

- Backend sends via `JavaMailSender` to `SMTP_HOST` (Mailpit in dev).
- Verification link: `${APP_BASE_URL}/verify?token=<single-use-token>` (token in URL is
  allowed — FR-P6).
- Tests read the captured message from **Mailpit's HTTP API** to extract the token and
  complete the flow deterministically (no real mail leaves the machine).
- Tokens: 24h TTL, single-use, prior-unused invalidated on resend (FR-A8/A11).

---

## 11. Frontend architecture

- **Routing (React Router):** `/signup`, `/login`, `/verify`, plus guarded app routes
  `/board`, `/teams`, `/epics`, `/tickets/:id`. Unauthed access redirects to `/login`.
- **Server state (TanStack Query):** all data via the API; cache + loading/error states.
  Browser storage is **never** the system of record (FR-P2).
- **API client:** typed fetch wrapper; sends credentials (cookie) + CSRF header; centralizes
  error-model parsing.
- **UX states (NFR-3):** shared components for loading, empty, error, success across screens.
- **Board (dnd-kit):** 5 columns; optimistic move on drop, **revert + error toast on API
  failure** (FR-B5); cards ordered most-recently-modified (FR-B7).
- **Screens** map 1:1 to FR-S1..S8.

---

## 12. Testing strategy & conventions

Minimum bar is NFR-6 (≥1 backend flow + ≥1 frontend/API flow); we exceed it deliberately.

**Backend (per BE ticket — mandatory):**
- **Unit (JUnit 5 + Mockito):** service/validation logic with **positive, negative, and
  boundary** cases. Collaborators mocked. (e.g., password length 7/8/128/129; duplicate
  email case-insensitive; expired/consumed token.)
- **Integration (Testcontainers):** real **Postgres** for repository + controller flows
  (MockMvc/RestAssured); real **Mailpit** container for email flows. Guarantees "runs on any
  dev's machine" without local installs.

**Frontend (per FE ticket — mandatory where applicable):**
- **Component (Vitest + React Testing Library):** rendering, validation, state transitions,
  loading/empty/error rendering. Positive/negative/boundary on form inputs.
- **API-contract (MSW):** mock backend success/validation-error/server-error; assert UI
  behavior (notably drag-drop revert-on-failure).

**E2E (Playwright — should-have, EP-08):** 3–4 specs on critical paths (DoD-1 signup→verify
via Mailpit→login; DoD-6 drag persists across refresh; DoD-3 ticket CRUD) run against
`docker compose`. Not blocking per-ticket.

**Coverage expectation:** every story has at least one BE integration test and one FE
component test covering its primary acceptance criteria. Each ticket lists its concrete test
cases.

---

## 13. Local run & common commands

```bash
# Full stack (from repo root)
cp .env.example .env
docker compose up --build           # app at http://localhost:8081, Mailpit UI at :8025

# Reset to a clean (empty) database
docker compose down -v && docker compose up --build

# Backend tests (Testcontainers needs Docker running)
cd backend && ./mvnw test

# Frontend tests
cd frontend && npm test              # vitest
npx playwright test                  # E2E (requires stack up)
```

---

## 14. Traceability conventions

- Every ticket header lists **Traceability:** the FR/NFR/DoD/AMB IDs it implements.
- The DoD gates (DoD-1…DoD-10) are the minimum end-to-end acceptance set; EP-08 closes them out.
- Ticket IDs use the `HTS-` prefix to avoid confusion with in-app ticket IDs (e.g. wireframe
  `TCK-1042`). Backlog inventory + status live in `docs/backlog/INDEX.md`.
- Any **new** ambiguity found during build is added to `requirements-analysis.md` §5 and
  resolved (explicit decision or agreed default) — never silently guessed.
