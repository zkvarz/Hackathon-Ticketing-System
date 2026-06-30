# Requirements Analysis — Hackathon Ticketing System

> **Purpose.** This document is the spec-driven analysis of `docs/requirements.md`. It
> restates the requirements as testable items, separates what is *stated* from what is
> *assumed*, and explicitly flags every ambiguity and open question instead of guessing.
> Anything not resolved here must be confirmed before implementation.
>
> **Source of truth:** `docs/requirements.md` (converted from
> `Hackathon_Ticketing_System_Requirements_v3.docx`). Wireframes in `docs/media/media/`.
>
> **Legend:** `FR` = functional requirement, `NFR` = non-functional requirement,
> `A` = assumption, `Q` = open question, `AMB` = ambiguity. IDs are stable; reference them
> in commits, tests, and PRs.

---

## 1. Summary

Build a small but complete **Kanban-style ticket tracker** as a **three-tier single-page
application** backed by a relational database. Registered, email-verified users organize
work **tickets** by **team**, optionally group them under **epics**, discuss them via
**comments**, and move them through a **fixed five-state Kanban workflow** by drag-and-drop.

**In scope (mandatory):** local email/password authentication with SMTP email
verification, team CRUD, epic CRUD, ticket CRUD, comments, and a draggable Kanban board
with filtering and search.

**Out of scope:** Scrum/sprints, SSO/OAuth, roles & membership, attachments/notifications,
custom workflows, and production-grade deployment.

**Hard delivery constraint:** from a clean checkout, the whole system must come up with
`docker compose up --build` from the repository root, with no host-installed runtimes
beyond Docker, on Windows/macOS/Linux.

The five canonical ticket states, in workflow order, are:
`new → ready_for_implementation → in_progress → ready_for_acceptance → done`.

---

## 2. Functional Requirements

Each item is phrased to be independently testable. **Stated** means it is explicit in the
source; cross-references to ambiguities/questions are noted inline.

### 2.1 Authentication & Accounts

| ID | Requirement | Source | Notes |
|----|-------------|:------:|-------|
| FR-A1 | Users sign up with email + password. | Stated | |
| FR-A2 | Email is trimmed, compared case-insensitively, and must be unique. | Stated | |
| FR-A3 | Users can log in and log out with local credentials only (no SSO). | Stated | |
| FR-A4 | Passwords must be ≥8 characters. | Stated | No max stated → see AMB-1. |
| FR-A5 | Passwords are never stored in plain text; hashed with an established algorithm (e.g. Argon2id). | Stated | "such as" → Argon2id recommended, not mandated. See AMB-2. |
| FR-A6 | After sign-up, the system sends an email-verification message via a configurable SMTP service; must support `relay1.dataart.com`. | Stated | See Q-1, Q-2. |
| FR-A7 | A newly registered account cannot use the main application until verified. | Stated | |
| FR-A8 | Verification tokens expire after 24 hours and are single-use. | Stated | |
| FR-A9 | Successful verification leads to the login screen; automatic login is **not** required. | Stated | |
| FR-A10 | An unverified user can request a new verification email from the login or verification-result screen. | Stated | |
| FR-A11 | Issuing a new verification token invalidates earlier unused tokens. | Stated | |
| FR-A12 | All app screens and API endpoints require authentication, **except** sign-up, login, email verification, and verification-email resend. | Stated | Static assets + optional health/readiness endpoints may be public. |

### 2.2 Teams

| ID | Requirement | Source | Notes |
|----|-------------|:------:|-------|
| FR-T1 | Authenticated users can view the list of teams. | Stated | |
| FR-T2 | Authenticated users can create, rename, and delete teams. | Stated | |
| FR-T3 | A team has at least: identifier, name, created timestamp, modified timestamp. | Stated | |
| FR-T4 | Team names are non-empty after trimming and unique case-insensitively. | Stated | |
| FR-T5 | A team cannot be deleted while it contains tickets **or** epics; show a clear validation message; no cascade. | Stated | Maps to HTTP 409 (FR-P4). |
| FR-T6 | All verified users can view and manage all teams (no ownership/membership). | Stated | |

### 2.3 Epics

| ID | Requirement | Source | Notes |
|----|-------------|:------:|-------|
| FR-E1 | Each epic belongs to exactly one team. | Stated | |
| FR-E2 | The team is chosen at epic creation and cannot change later. | Stated | |
| FR-E3 | Provide a separate screen for epic CRUD (create, list, edit, delete). | Stated | |
| FR-E4 | An epic has at least: identifier, team reference, title, optional description, created + modified timestamps. | Stated | |
| FR-E5 | Epic titles are non-empty after trimming. | Stated | |
| FR-E6 | A ticket may optionally reference one epic, chosen from a drop-down. | Stated | |
| FR-E7 | A ticket may reference only an epic in the **same team**; the backend must enforce this. | Stated | |
| FR-E8 | An epic cannot be deleted while tickets reference it; show a clear validation message. | Stated | Maps to HTTP 409. |

### 2.4 Tickets

Ticket fields (from the spec table):

| Field | Required | Type / values | Notes |
|-------|:--------:|---------------|-------|
| ID | Yes | System-generated | Stable, unique. |
| Team | Yes | Reference to a team | Determines the board; must exist. |
| Type | Yes | `bug \| feature \| fix` | Classification labels only; no workflow difference. |
| State | Yes | `new \| ready_for_implementation \| in_progress \| ready_for_acceptance \| done` | Canonical API values; UI shows human-readable labels. |
| Epic | No | Reference to an epic | Null or an epic from the same team. |
| Title | Yes | Text | Non-empty after trimming; no mandated max length. |
| Body | Yes | Long text | Non-empty; plain text or Markdown; no mandated max length. |
| Created at | Yes | Timestamp | Server-set, UTC, at creation. |
| Modified at | Yes | Timestamp | Server-set, UTC, on field/state change. Comments do **not** change it. |
| Created by | Yes | Reference to user | Set from the authenticated user. |

| ID | Requirement | Source | Notes |
|----|-------------|:------:|-------|
| FR-K1 | Create a ticket. | Stated | |
| FR-K2 | Open/view all ticket fields, including created by/at and modified at. | Stated | |
| FR-K3 | Edit ticket type, team, epic, title, body, and state. | Stated | |
| FR-K4 | Modified-at reflects the latest *actual* change; saving unchanged values must not advance it. | Stated | Requires field-level diffing. See AMB-3. |
| FR-K5 | When a ticket's team changes, the UI clears/replaces the selected epic; backend rejects a ticket whose epic belongs to another team. | Stated | |
| FR-K6 | Delete a ticket after explicit confirmation; deleting a ticket also deletes its comments. | Stated | Comment cascade is intentional (contrast FR-T5). |
| FR-K7 | Drag-and-drop state changes are persisted immediately in the DB. | Stated | |
| FR-K8 | Backend validates all submitted enum values and references; client-side validation alone is insufficient. | Stated | |

### 2.5 Comments

| ID | Requirement | Source | Notes |
|----|-------------|:------:|-------|
| FR-C1 | Authenticated users can add comments to a ticket. | Stated | |
| FR-C2 | A comment has: identifier, ticket reference, author, body, created timestamp. | Stated | |
| FR-C3 | Comment bodies are non-empty. | Stated | |
| FR-C4 | Comments display chronologically, oldest first. | Stated | |
| FR-C5 | Adding a comment does not update the ticket modified-at and does not change board ordering. | Stated | |
| FR-C6 | Comments are immutable after creation (mandatory scope); edit/delete is a stretch feature. | Stated | |

### 2.6 Kanban Board

| ID | Requirement | Source | Notes |
|----|-------------|:------:|-------|
| FR-B1 | The primary screen is a Kanban board for one selected team. | Stated | |
| FR-B2 | Exactly five columns, one per state, in workflow order. | Stated | |
| FR-B3 | Each card shows at least title and type; showing epic is recommended. | Stated | |
| FR-B4 | Users can drag a card between columns; dropping changes state and persists via API. | Stated | |
| FR-B5 | If a drag-drop update fails, the card returns to its previous column and an error is shown. | Stated | |
| FR-B6 | Cards may move directly between any two states; sequential transitions are not enforced. | Stated | |
| FR-B7 | Within a column, cards are ordered most-recently-modified first; no custom manual order persisted. | Stated | |
| FR-B8 | The board provides a clear way to create a ticket and open an existing ticket. | Stated | |
| FR-B9 | Provide at minimum: filter by type, filter by epic, and case-insensitive substring search over title. Filters combine with AND; client- or server-side is acceptable. | Stated | |
| FR-B10 | The interface remains usable with at least 100 tickets on one team board. | Stated | "Usable" undefined → see AMB-4. |

### 2.7 API & Persistence

| ID | Requirement | Source | Notes |
|----|-------------|:------:|-------|
| FR-P1 | All create/update/delete operations go through the backend API and persist in the RDBMS. | Stated | |
| FR-P2 | The app must not rely on browser local storage as the system of record. | Stated | |
| FR-P3 | Use DB constraints and/or server-side validation to maintain referential integrity. | Stated | |
| FR-P4 | Return meaningful HTTP status codes/messages for validation, auth, missing-record, and conflict cases. Deleting a referenced team/epic returns **HTTP 409 Conflict**. | Stated | |
| FR-P5 | IDs may be UUIDs or DB-generated numeric values. API timestamps use ISO-8601 in UTC. | Stated | |
| FR-P6 | Cookie sessions or bearer tokens both acceptable. Session/access/bearer tokens must not appear in URLs. A single-use email-verification token may be in the verification URL. | Stated | |
| FR-P7 | Concurrent-edit conflict detection is not required; last successful write wins. | Stated | |
| FR-P8 | Schema creation is automated via migrations or an equivalent repeatable mechanism. | Stated | |
| FR-P9 | After migrations, a fresh DB contains no application data (migration metadata allowed). No seed/sample data is loaded by default; QA creates data via UI/API. | Stated | |

### 2.8 Required Screens (FR-S)

| ID | Screen |
|----|--------|
| FR-S1 | Sign-up screen. |
| FR-S2 | Email verification result screen. |
| FR-S3 | Verification-email resend action (unverified / expired-token cases). |
| FR-S4 | Login screen. |
| FR-S5 | Kanban board with team selector. |
| FR-S6 | Ticket create/edit/details view. |
| FR-S7 | Team management screen. |
| FR-S8 | Epic management screen. |

### 2.9 Definition of Done (acceptance criteria)

These are the spec's own acceptance gates; treat them as end-to-end test scenarios.

- **DoD-1** A user can sign up, receive a verification email via the configured SMTP service, verify, and log in.
- **DoD-2** Teams and epics can be managed through the UI and persist.
- **DoD-3** A verified user can create, view, edit, and delete tickets.
- **DoD-4** A user can add comments and see author + timestamp.
- **DoD-5** The board shows tickets in the correct state columns for the selected team.
- **DoD-6** Dragging a ticket to another column updates the server and survives a page refresh.
- **DoD-7** The app starts from a clean checkout with `docker compose up --build` from the repo root.
- **DoD-8** No hard-coded user password or committed secret.
- **DoD-9** A fresh DB starts with schema + migration metadata only; no preloaded app data.
- **DoD-10** QA can create all required test/demo data via UI or API without manual DB edits.

### 2.10 Stretch Features (non-mandatory)

Password reset flow; edit/delete own comments; ticket activity history; virtualized
rendering for large boards.

---

## 3. Non-Functional Requirements

| ID | Category | Requirement | Notes |
|----|----------|-------------|-------|
| NFR-1 | Security | Protect authenticated endpoints, hash passwords, validate input, do not expose credentials or SMTP secrets in source control. | Reinforces FR-A5, FR-A12, DoD-8. |
| NFR-2 | Reliability | A browser refresh or app restart must not lose persisted data. | |
| NFR-3 | Usability | Display loading, empty, success, and error states where applicable. | |
| NFR-4 | Compatibility | Support a current desktop version of Chrome, Edge, or Firefox. | Desktop only; no mobile requirement → see AMB-5. |
| NFR-5 | Maintainability | Include a README with prerequisites, configuration, and startup commands. | |
| NFR-6 | Testing | Automated tests covering ≥1 backend business flow and ≥1 frontend or API flow. | Minimum bar, not full coverage. |
| NFR-7 | Portability | Cross-platform; runs on a clean Windows/macOS/Linux laptop via Docker Compose only. | Ties to DoD-7. |
| NFR-8 | Scale | Board usable with ≥100 tickets per team. | Quantified in FR-B10; "usable" undefined (AMB-4). |

---

## 4. Architecture

### 4.1 Mandated by the spec

- **Three logical tiers**, clearly separated: presentation (SPA) / application (HTTP API) /
  persistence (RDBMS).
- **SPA frontend** consuming an **HTTP API**.
- **Server-based RDBMS** in its own container — **PostgreSQL** is the named example.
- Frontend and backend may be **separate containers**, *or* the backend may serve the
  compiled SPA, as long as the three logical tiers stay separated.
- **`docker compose up --build`** from the repo root brings up the entire system; no
  host-installed frontend/backend/DB runtimes.
- **Automated migrations** for schema; **empty** application data on a fresh start.
- **Configurable SMTP** (must support `relay1.dataart.com`); secrets kept out of source control.
- Auth via **cookie sessions or bearer tokens**; tokens never in URLs (except the
  single-use email-verification token).
- **ISO-8601 UTC** timestamps; **UUID or numeric** IDs.

### 4.2 Implied data model (derived, for design — confirm before building)

Entities and key relationships drawn from §§4–7 of the spec:

- **User** — id, email (unique, case-insensitive), password hash, verified flag, timestamps.
- **EmailVerificationToken** — id, user ref, token, expires_at (24h), used/invalidated state.
- **Team** — id, name (unique CI), created_at, modified_at.
- **Epic** — id, team ref (immutable), title, optional description, created_at, modified_at.
- **Ticket** — id, team ref, type, state, optional epic ref (same team), title, body,
  created_at, modified_at, created_by.
- **Comment** — id, ticket ref, author ref, body, created_at. (Cascade-deleted with ticket.)

Referential rules: team delete blocked if it has tickets/epics; epic delete blocked if
referenced by tickets; ticket delete cascades comments; ticket↔epic must share a team.

> These entities are a design *derivation*, not a spec mandate. The spec says "at least"
> for several field sets, so additional fields are permitted.

### 4.3 Resolved technology decisions

The spec leaves languages/frameworks unrestricted; the following stack has been **agreed**
for this project and supersedes the "open" status of Q-3 and Q-5:

| Tier | Decision | Rationale |
|------|----------|-----------|
| **Backend** | **Spring Boot** (Java; Kotlin acceptable within the Spring Boot app) | Maps 1:1 to the spec: Spring Security (Argon2id hashing, endpoint auth), Spring Data JPA + Flyway/Liquibase (migrations, empty-on-start DB), Spring Mail (SMTP → Mailpit/relay), Testcontainers (real-Postgres integration tests). Implementer's home stack. |
| **Frontend** | **TypeScript + React** SPA (mainstream tooling, e.g. Vite; mature drag-drop library such as dnd-kit / Pragmatic drag-and-drop) | Lowest-risk path to the hard drag-and-drop requirements (FR-B4–B7) and the "usable with 100 tickets" bar (FR-B10). Large, mature ecosystem suits hackathon time pressure. |
| **Database** | **PostgreSQL**, dedicated container | Explicitly endorsed by the spec example. |
| **Auth** | **Server-side session cookies** (`HttpOnly`/`Secure`/`SameSite`), 8h absolute + 30 min idle timeout | Trivial logout (FR-A3) + survives refresh (NFR-2); near-free in Spring Security. See A-3 / AMB-7. |
| **IDs** | **UUIDv7** primary keys | Non-enumerable in URLs + time-ordered index locality. See A-5 / FR-P5. |
| **Email (dev/test)** | **Dockerized Mailpit** | Captures verification email locally; real `relay1.dataart.com` selectable via env vars (see A-4). |
| **Packaging** | **Separate frontend and backend containers** in a single **monorepo**; `docker compose` at the repo root orchestrates db + mailpit + backend + frontend | Explicitly allowed by the spec; keeps the three logical tiers cleanly separated (FR §2 / §4.1) and `docker compose up --build` at the root (DoD-7). |

Repository layout:

```
/ (repo root)
├── docker-compose.yml        # orchestrates db, mailpit, backend, frontend
├── .env.example              # documented secrets/config keys (A-7)
├── backend/                  # Spring Boot app + its Dockerfile
├── frontend/                 # React + TypeScript SPA + its Dockerfile (static build served by nginx)
├── docs/
└── README.md
```

Auth and ID strategy are now **decided** (previously open): **server-side session cookies**
(A-3, resolves AMB-7) and **UUIDv7** primary keys (A-5).

---

## 5. Ambiguities (explicitly flagged — do not guess)

> **Status update.** AMB-1, 2, 3, 4, 5, 8, 9, 10 are **confirmed** — their proposed defaults
> are now binding requirements. AMB-6 is **resolved** (Mailpit, see §A-4). AMB-7 is
> **resolved** below (auth-mechanism decision in §4.3 / A-3).

| ID | Ambiguity | Impact | Resolution (binding) |
|----|-----------|--------|----------------------|
| AMB-1 | Title, body, and password have **no maximum length** specified. | DB column sizing, validation, UI. | **Confirmed.** Apply DB-backed limits: password ≤128, title ≤200, body ≤10k chars; document them and enforce server-side. |
| AMB-2 | Password hashing says "such as Argon2id" — recommendation, not mandate. | Security posture, dependency choice. | **Confirmed.** Use Argon2id; fall back to bcrypt only if Argon2id is impractical in the chosen stack. |
| AMB-3 | "Modified-at reflects latest *actual* change" — does state change via drag-drop count, and does it require field-level diffing? | Timestamp correctness, board ordering. | **Confirmed.** Any change to a tracked field/state (including drag-drop) advances modified-at; a save with no field delta does not. |
| AMB-4 | "Usable with ≥100 tickets" — no latency/FPS target given. | Performance bar, whether virtualization is needed. | **Confirmed.** Target smooth interaction with 100 cards without virtualization; virtualization remains a stretch (FR §2.10). |
| AMB-5 | Compatibility lists desktop browsers only — is responsive/mobile layout expected? | UI effort, layout strategy. | **Confirmed.** Desktop-first; no mobile/responsive guarantee. |
| AMB-6 | "Configurable SMTP" — configured how, and dev/test behavior when SMTP is unreachable? | Compose setup, local dev, testing. | **Resolved.** Configure via env vars; use a dockerized **Mailpit** instance as the SMTP target for local dev/testing instead of a real relay. See A-4 / Q-1 / Q-2. |
| AMB-7 | Auth mechanism is a free choice (cookie session vs. bearer token), but the spec does not state session lifetime / token expiry for the *app* session (only the 24h verification token). | Security, UX. | **Resolved.** Use **server-side session cookies** (see A-3); session lifetime **8h absolute + 30 min idle/sliding timeout**, logout invalidates the session server-side (satisfies FR-A3). No "remember me" in scope. |
| AMB-8 | "Created by" is required on tickets, but comment **author** display name source is unspecified (email? a separate display name?). | UI, user model. | **Confirmed.** Display the user's email as author identity (no separate profile in scope). |
| AMB-9 | Team rename uniqueness on collision, and case-insensitive comparison locale, are not specified. | Validation correctness. | **Confirmed.** Enforce CI uniqueness using a normalized (lower-cased, trimmed) form; document the normalization. |
| AMB-10 | Filtering/search may be client- or server-side — interacts with the ≥100-ticket bar and pagination, which is not mentioned. | Architecture, performance. | **Confirmed.** Server-side filtering/search; no pagination unless needed for the 100-ticket bar. |

---

## 6. Assumptions (will proceed on these unless told otherwise)

- **A-1 — Stack:** **Decided** — Spring Boot backend (Java) + TypeScript/React SPA frontend,
  PostgreSQL database. See §4.3 for the full decision table. (Resolves Q-3.)
- **A-2 — Database:** PostgreSQL, in its own container, as the example explicitly endorses.
- **A-3 — Auth:** **Decided** — **server-side session cookies** (`HttpOnly`, `Secure`,
  `SameSite`), with Spring Security's built-in CSRF protection. Chosen over stateless bearer
  tokens because mandatory logout (FR-A3) and refresh-survives (NFR-2) are trivial with
  sessions, while statelessness buys nothing here (HA is out of scope). Session lifetime:
  8h absolute + 30 min idle (sliding); logout invalidates server-side; no "remember me".
  (Resolves AMB-7.)
- **A-4 — SMTP in dev:** Local/dev/CI uses a **dockerized Mailpit** instance as the SMTP
  target (no real mail leaves the machine; messages are inspected via Mailpit's web UI/API).
  Mailpit ships as a service in `docker compose`, so the full sign-up → verification email →
  verify → login flow (DoD-1) is demonstrable and testable without a real relay.
  `relay1.dataart.com` remains the supported **real** relay, selectable purely via SMTP env
  vars (host/port/credentials) — switching from Mailpit to the real relay is config-only, no
  code change.
- **A-5 — IDs:** **Decided** — **UUIDv7** primary keys: non-enumerable in URLs
  (defense-in-depth on top of FR-A12 auth) and time-ordered, so index locality stays close
  to a sequential numeric key. Supported by PostgreSQL + Hibernate.
- **A-6 — Timestamps:** All persisted and API timestamps are UTC ISO-8601.
- **A-7 — Secrets:** SMTP and DB credentials supplied via environment variables / Compose
  env files that are **not** committed; an `.env.example` documents the keys.
- **A-8 — No seed data:** Default startup loads schema + migration metadata only; any demo
  data is created through the UI/API per DoD-9/DoD-10.
- **A-9 — Separate containers:** **Decided** — frontend and backend ship as **separate
  containers** in a single monorepo (folders `frontend/` and `backend/`), orchestrated by a
  root `docker-compose.yml`. (Resolves Q-5.)
- **A-10 — "Usable" board:** Interpreted per AMB-4 (smooth with 100 cards, no virtualization
  required).

---

## 7. Open Questions (all resolved)

> **Status.** Q-1…Q-9 are all resolved — by an explicit decision (Q-1, Q-2, Q-3, Q-5) or by
> a confirmed ambiguity default (Q-4, Q-6, Q-7, Q-8, Q-9). No questions block implementation.

| ID | Question | Blocks | Why it matters |
|----|----------|--------|----------------|
| Q-1 | ~~Will a working `relay1.dataart.com` relay be available during dev/QA?~~ **Resolved:** dev/QA/testing use a dockerized **Mailpit** instance; a real relay is not required to demonstrate the flow. The real `relay1.dataart.com` relay remains config-selectable via env vars for any real-delivery scenario. | DoD-1 | — |
| Q-2 | ~~Expected behavior when outbound SMTP is blocked?~~ **Resolved:** Mailpit runs as a compose service and captures all mail locally, so the verification flow is deterministic and self-contained in local/CI runs (see AMB-6, A-4). | NFR-6, local dev | — |
| Q-3 | ~~Preferred stack/language/framework?~~ **Resolved:** Spring Boot (Java) backend + TypeScript/React SPA frontend + PostgreSQL. See §4.3 / A-1. | A-1 | — |
| Q-4 | ~~Field-length limits / content rules beyond the stated minimums?~~ **Resolved** via AMB-1: password ≤128, title ≤200, body ≤10k chars, enforced server-side. | AMB-1 | — |
| Q-5 | ~~Separate containers or single backend-serves-SPA container?~~ **Resolved:** separate frontend/backend containers in a monorepo, root `docker-compose.yml`. See §4.3 / A-9. | A-9, Compose layout | — |
| Q-6 | ~~Required app session/token lifetime and idle-timeout?~~ **Resolved** via AMB-7 / A-3: session cookies, 8h absolute + 30 min idle, server-side logout, no "remember me". | AMB-7 | — |
| Q-7 | ~~Is a responsive/mobile layout expected?~~ **Resolved** via AMB-5: desktop-first, no mobile/responsive guarantee. | AMB-5 | — |
| Q-8 | ~~Author / created-by display: email or separate display name?~~ **Resolved** via AMB-8: display the user's email as author identity (no separate profile in scope). | AMB-8 | — |
| Q-9 | ~~Server-side pagination for boards beyond 100 tickets?~~ **Resolved** via AMB-4 / AMB-10: server-side filtering/search; render up to the 100-ticket bar without pagination; pagination only if needed beyond it. | AMB-4, AMB-10 | — |

---

## 8. Traceability & next step

- Every FR/NFR ID above should map to: a migration/model, an API endpoint, a UI element,
  and at least one automated test where applicable. The DoD items (DoD-1…DoD-10) are the
  minimum end-to-end acceptance set.
- **All ambiguities (AMB-1…AMB-10) and open questions (Q-1…Q-9) are now resolved** — each
  by an explicit decision or a confirmed default recorded in §4.3/§5/§6. Implementation is
  unblocked. Any *new* ambiguity discovered during build must be added here and resolved the
  same way (explicit decision or agreed default) rather than guessed.

_This analysis is derived solely from `docs/requirements.md` and the wireframes; where the
source is silent, that silence is recorded above rather than resolved by guessing._
