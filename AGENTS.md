# AGENTS.md — Start Here

Onboarding guide for any developer or AI agent picking up this project. Read this first, then
the documents it points to. **This project is spec-driven:** the docs are the source of truth,
and code follows them. If something is unclear, resolve it in the docs (see §6) — never guess.

---

## 1. What this project is

A **Kanban-style ticketing system**: a three-tier single-page app (React + TypeScript SPA →
Spring Boot REST API → PostgreSQL), with local email/password auth and SMTP email verification.
The entire system runs from a clean checkout with `docker compose up --build`.

Mandatory scope: authentication, teams, epics, tickets, comments, and a draggable Kanban board.
Out of scope: Scrum/sprints, SSO/OAuth, roles/membership, attachments/notifications.

---

## 2. Read these first (in order)

| # | File | Why |
|---|------|-----|
| 1 | [`docs/requirements.md`](docs/requirements.md) | The original spec (converted from the source `.docx`). The ground truth. |
| 2 | [`docs/requirements-analysis.md`](docs/requirements-analysis.md) | Spec-driven analysis: functional/non-functional requirements with stable IDs (FR-/NFR-/DoD-), plus **all resolved ambiguities (AMB-) and decisions**. |
| 3 | [`docs/architecture.md`](docs/architecture.md) | **The keystone.** Tech stack, folder structure, container/compose topology, data model/ERD, API surface + error model, auth/email flows, frontend architecture, and testing conventions. |
| 4 | [`docs/backlog/INDEX.md`](docs/backlog/INDEX.md) | Master inventory of all tickets (`HTS-001`…`HTS-043`), their dependencies, requirement traceability, status, and the recommended build order. |

Then open the specific ticket you're implementing under `docs/backlog/tickets/EP-NN/`.

> **Rule of precedence:** if `architecture.md` and a ticket disagree, `architecture.md` wins —
> fix the ticket. If the spec/analysis and `architecture.md` disagree, escalate (see §6).

---

## 3. Repository structure

```
/ (repo root)
├── AGENTS.md                 # this file — start here
├── README.md                 # human-facing setup & run guide
├── docker-compose.yml        # db, mailpit, backend, frontend (created in HTS-001)
├── .env.example              # documented config/secret keys; copy to .env (never commit .env)
├── .gitignore                # excludes IDE files (.idea/), build output, secrets
├── .github/workflows/        # CI (HTS-004)
├── backend/                  # Spring Boot (Java 21, Maven) — see architecture.md §3
├── frontend/                 # React + TypeScript (Vite) — see architecture.md §3
└── docs/
    ├── requirements.md
    ├── requirements-analysis.md
    ├── architecture.md
    ├── media/media/          # wireframes referenced by tickets
    └── backlog/
        ├── INDEX.md           # ticket tracking table + build order
        ├── templates/         # ticket-template.md
        ├── EP-01-foundation.md … EP-09-stretch.md   # epic summaries
        └── tickets/EP-NN/HTS-XXX-*.md                # individual tickets
```

Full detail (per-tier package/folder layout, container ports, config keys) is in
`architecture.md` §3–§5.

---

## 4. How to implement a ticket

1. **Pick the next `TODO`** ticket respecting dependencies and the build order in `INDEX.md`.
   Foundation (EP-01) first; within a story, **build the BE ticket before its FE ticket**.
2. **Set status to `IN-PROGRESS`** in `INDEX.md`.
3. **Read the ticket** end to end: Scope, Technical approach, Acceptance criteria, Test plan.
   Follow the architecture references it cites instead of inventing structure.
4. **Implement to the acceptance criteria** — they map to FR/NFR/DoD IDs.
5. **Write the tests the ticket lists** (see §5). Tests are not optional.
6. **Run and verify** using the ticket's "How to run / verify" commands.
7. **Update `INDEX.md` status** to `IN-REVIEW`/`DONE` and tick the ticket's DoD checklist.

---

## 5. Engineering standards (non-negotiable)

- **Backend (each BE ticket):** unit tests with **JUnit 5 + Mockito** covering **positive,
  negative, and boundary** cases; integration tests with **Testcontainers** (real PostgreSQL,
  and **Mailpit** for email flows) so anything runs on any machine with Docker.
- **Frontend (each FE ticket):** component tests with **Vitest + React Testing Library**, and
  **MSW** for API-contract behavior (success / validation-error / server-error). Cover
  loading/empty/error/success states (NFR-3).
- **E2E:** a small should-have **Playwright** suite (`HTS-036`) — not per-ticket.
- **Validation lives on the server** (FR-K8); client validation is UX only.
- **No secrets in source.** All config via env (`.env` from `.env.example`); never commit `.env`
  or IDE files. CI must stay green (`HTS-004`).
- **Conventions:** `HTS-` prefix for dev tickets (distinct from in-app ticket IDs like
  `TCK-1042`); UUIDv7 IDs; ISO-8601 UTC timestamps; standardized API error model
  (architecture.md §8).

### Build / run / test
```bash
# Whole stack
cp .env.example .env && docker compose up --build      # app :8081, Mailpit UI :8025
docker compose down -v && docker compose up --build    # reset to an empty DB

# Backend (Docker required for Testcontainers)
cd backend && ./mvnw verify

# Frontend
cd frontend && npm ci && npm test
npx playwright test                                     # E2E (stack must be up)
```

---

## 6. If requirements change — update the docs FIRST (spec-driven protocol)

Do **not** change code to match a new requirement without updating the documents. Apply changes
in this order so traceability stays intact:

1. **New/changed requirement, ambiguity, or decision →
   [`docs/requirements-analysis.md`](docs/requirements-analysis.md).**
   Add or amend the relevant `FR-`/`NFR-`/`AMB-`/`Q-` item. **Never silently guess** an unclear
   point: record it as an ambiguity (§5) with either an explicit decision or an agreed default,
   then proceed. (This is the standing rule from analysis §8.)
2. **Structural/technical impact (stack, folders, data model, API, flows, testing) →
   [`docs/architecture.md`](docs/architecture.md).** Keep its decision tables current; it is the
   precedence authority for code.
3. **Scope/work impact → [`docs/backlog/INDEX.md`](docs/backlog/INDEX.md) and the affected
   ticket files.** Add/modify/retire `HTS-` tickets, update dependencies, traceability, and
   status. Use `docs/backlog/templates/ticket-template.md` for new tickets.
4. **If the original spec itself changes** (a new `Hackathon_Ticketing_System_Requirements_v*.docx`):
   re-run the pandoc conversion documented in `README.md` to refresh `docs/requirements.md` and
   `docs/media/media/`, then propagate through steps 1–3.
5. **User-facing setup/run changes → [`README.md`](README.md).**

> Golden rule: **docs lead, code follows.** A change isn't "done" until the analysis,
> architecture, backlog, and (if relevant) README all reflect it.

---

## 7. Quick reference

- **Where do I start coding?** `HTS-001` → `HTS-002` → `HTS-003` (foundation), then EP-02
  (auth gates everything). See `INDEX.md` build order.
- **What does "done" mean?** The ticket's acceptance criteria + DoD checklist are met, its
  tests pass, and `INDEX.md` is updated. The project-level gate is `DoD-1…DoD-10` (closed out by
  `HTS-035`).
- **Something's ambiguous?** Don't guess — record it in `requirements-analysis.md` §5 and resolve
  it there first.
