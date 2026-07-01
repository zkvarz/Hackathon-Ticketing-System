# Backlog Index — Hackathon Ticketing System

Master inventory and tracking table for all epics, stories, and implementation tickets.
Tickets use the `HTS-` prefix (distinct from in-app ticket IDs like the wireframe's `TCK-1042`).
Shared structural detail lives in [`../architecture.md`](../architecture.md); requirement IDs
come from [`../requirements-analysis.md`](../requirements-analysis.md).

**Status legend:** `TODO` · `IN-PROGRESS` · `IN-REVIEW` · `DONE` · `BLOCKED`
**Type legend:** `BE` backend · `FE` frontend · `INFRA` infrastructure · `DOCS` · `QA/E2E`

> Rule (from analysis §8): feature stories have **separate BE and FE tickets**. Each ticket is
> independently implementable, testable, and runnable. BE tickets carry unit (Mockito,
> positive/negative/boundary) + integration (Testcontainers) tests; FE tickets carry component
> (Vitest/RTL) + API-contract (MSW) tests. E2E (Playwright) is a should-have suite in EP-08.

---

## Epics

| Epic | Title | Goal | Status |
|------|-------|------|--------|
| EP-01 | Foundation | Monorepo, Compose, BE/FE baselines, CI | TODO |
| EP-02 | Authentication & Accounts | Signup, email verification, login/logout, session, guards | TODO |
| EP-03 | Teams | Team CRUD + validation + referenced-delete 409 | TODO |
| EP-04 | Epics | Epic CRUD + same-team rule + referenced-delete 409 | TODO |
| EP-05 | Tickets | Ticket CRUD + enum/ref validation + modified-at semantics | TODO |
| EP-06 | Comments | Add + chronological list + immutability | TODO |
| EP-07 | Kanban Board | Board view, drag-drop persist/revert, filters & search | TODO |
| EP-08 | Cross-cutting & Delivery | Error model, UX states, security, README, DoD, E2E | TODO |
| EP-09 | Stretch (optional) | Password reset, comment edit/delete, history, virtualization | TODO |
| EP-10 | Improvements & Tech Debt (optional) | Perf + internal-quality items found during build | TODO |

---

## Ticket inventory

### EP-01 Foundation
| ID | Type | Title | Story | Deps | Traceability | Status |
|----|------|-------|-------|------|--------------|--------|
| HTS-001 | INFRA | Monorepo + docker-compose scaffold (db, mailpit, backend, frontend) + .env.example | ST-01 | — | §2,§4,§5; DoD-7,DoD-8 | DONE |
| HTS-002 | BE | Spring Boot baseline: config, Postgres, Flyway baseline, health endpoint, Dockerfile | ST-02 | HTS-001 | §3,§7; FR-P8,FR-P9 | DONE |
| HTS-003 | FE | Vite + React + TS baseline: router shell, API client, layout, Dockerfile (nginx) | ST-03 | HTS-001 | §11; NFR-3,NFR-4 | DONE |
| HTS-004 | INFRA | GitHub Actions CI: build + test BE & FE (Testcontainers) | ST-04 | HTS-002,HTS-003 | NFR-6 | DONE |

### EP-02 Authentication & Accounts
| ID | Type | Title | Story | Deps | Traceability | Status |
|----|------|-------|-------|------|--------------|--------|
| HTS-005 | BE | Sign-up endpoint: validation, Argon2id hash, unique-CI email, persist unverified | ST-01 | HTS-002 | FR-A1,A2,A4,A5; AMB-1,AMB-2 | DONE |
| HTS-006 | FE | Sign-up screen: form, validation, success/error states | ST-01 | HTS-003,HTS-005 | FR-S1; NFR-3 | DONE |
| HTS-007 | BE | Email verification: token issue + SMTP send + verify endpoint (24h, single-use) | ST-02 | HTS-005 | FR-A6,A7,A8,A9; DoD-1 | DONE |
| HTS-008 | FE | Email verification result screen (success / expired-invalid) | ST-02 | HTS-003,HTS-007 | FR-S2 | DONE |
| HTS-009 | BE | Resend verification endpoint (invalidate prior unused tokens) | ST-03 | HTS-007 | FR-A10,A11 | DONE |
| HTS-010 | FE | Resend verification action (login + verification screens) | ST-03 | HTS-008,HTS-009 | FR-S3 | DONE |
| HTS-011 | BE | Login/logout: session cookie, reject unverified, lifetime config | ST-04 | HTS-007 | FR-A3,A7; AMB-7; DoD-1 | DONE |
| HTS-012 | FE | Login screen + logout control (user menu) | ST-04 | HTS-003,HTS-011 | FR-S4 | DONE |
| HTS-013 | BE | Secure all endpoints except auth set; CSRF; 401/403 error model | ST-05 | HTS-011 | FR-A12; FR-P4,P6; NFR-1 | DONE |
| HTS-014 | FE | Route guards + auth context + 401 handling/redirect | ST-05 | HTS-012,HTS-013 | FR-A12; NFR-2 | DONE |

### EP-03 Teams
| ID | Type | Title | Story | Deps | Traceability | Status |
|----|------|-------|-------|------|--------------|--------|
| HTS-015 | BE | Team CRUD: validation, CI-unique name, 409 on delete-with-children | ST-01 | HTS-013 | FR-T1..T6; FR-P4; AMB-9 | DONE |
| HTS-016 | FE | Team management screen: list, create, rename, delete (disabled when referenced) | ST-01 | HTS-014,HTS-015 | FR-S7; NFR-3 | DONE |

### EP-04 Epics
| ID | Type | Title | Story | Deps | Traceability | Status |
|----|------|-------|-------|------|--------------|--------|
| HTS-017 | BE | Epic CRUD: team-fixed, title validation, 409 on delete-with-tickets | ST-01 | HTS-015 | FR-E1..E5,E8; FR-P4 | DONE |
| HTS-018 | FE | Epic management screen: team selector, list, create/edit/delete | ST-01 | HTS-016,HTS-017 | FR-S8; NFR-3 | DONE |

### EP-05 Tickets
| ID | Type | Title | Story | Deps | Traceability | Status |
|----|------|-------|-------|------|--------------|--------|
| HTS-019 | BE | Ticket CRUD: fields, enum validation, created/modified-by/at, modified-at semantics, delete cascades comments | ST-01 | HTS-017 | FR-K1..K4,K6,K8; AMB-1,AMB-3 | DONE |
| HTS-020 | FE | Ticket create/edit/details view | ST-01 | HTS-018,HTS-019 | FR-S6; NFR-3 | DONE |
| HTS-021 | BE | Epic-same-team enforcement + team-change epic-reset validation | ST-02 | HTS-019 | FR-E7,FR-K5 | DONE |
| HTS-022 | FE | Team/epic dropdown linkage (team change clears/replaces epic) | ST-02 | HTS-020,HTS-021 | FR-K5; FR-E6 | DONE |

### EP-06 Comments
| ID | Type | Title | Story | Deps | Traceability | Status |
|----|------|-------|-------|------|--------------|--------|
| HTS-023 | BE | Comment add + list: chronological, non-empty, immutable, no modified-at bump | ST-01 | HTS-019 | FR-C1..C6 | DONE |
| HTS-024 | FE | Comments panel: list oldest-first, add comment | ST-01 | HTS-020,HTS-023 | FR-C4; FR-S6 | DONE |

### EP-07 Kanban Board
| ID | Type | Title | Story | Deps | Traceability | Status |
|----|------|-------|-------|------|--------------|--------|
| HTS-025 | BE | Board query: tickets by team, 5 states, most-recently-modified order | ST-01 | HTS-019 | FR-B1,B2,B7 | DONE |
| HTS-026 | FE | Board UI: 5 columns, cards (title+type+epic), team selector, counts | ST-01 | HTS-020,HTS-025 | FR-B1,B2,B3,B8,B10; FR-S5 | DONE |
| HTS-027 | BE | State-change endpoint: validate enum, persist, advance modified-at | ST-02 | HTS-019 | FR-K7,FR-B4,B6; FR-P1 | DONE |
| HTS-028 | FE | Drag-drop (dnd-kit): optimistic move + revert-on-failure + error | ST-02 | HTS-026,HTS-027 | FR-B4,B5,B6 | DONE |
| HTS-029 | BE | Server-side filter (type, epic) + title substring search (AND) | ST-03 | HTS-025 | FR-B9; AMB-10 | DONE |
| HTS-030 | FE | Filter/search controls + wiring + clear | ST-03 | HTS-026,HTS-029 | FR-B9; NFR-3 | DONE |

### EP-08 Cross-cutting & Delivery
| ID | Type | Title | Story | Deps | Traceability | Status |
|----|------|-------|-------|------|--------------|--------|
| HTS-031 | BE | Standardized error response model + global exception handling + status codes | ST-01 | HTS-013 | FR-P4; NFR-1 | DONE |
| HTS-032 | FE | Global loading/empty/error/success UX + error boundary/toast | ST-01 | HTS-003 | NFR-3 | DONE |
| HTS-033 | BE | Security hardening: env secrets, headers, no committed secrets, CSRF/SameSite review | ST-02 | HTS-013 | NFR-1; DoD-8 | DONE |
| HTS-034 | DOCS | README: prerequisites, configuration, startup, run/test commands | ST-03 | HTS-001 | NFR-5; DoD-7 | DONE |
| HTS-035 | QA | Fresh-DB/no-seed verification + DoD acceptance checklist run-through | ST-04 | (most) | DoD-1..DoD-10 | TODO |
| HTS-036 | QA/E2E | Playwright critical-path suite (signup→verify→login, drag persists, ticket CRUD) — should-have | ST-05 | HTS-028,HTS-035 | DoD-1,DoD-3,DoD-6 | TODO |

### EP-09 Stretch (optional, non-blocking)
| ID | Type | Title | Story | Deps | Traceability | Status |
|----|------|-------|-------|------|--------------|--------|
| HTS-037 | BE | Password reset flow (backend) | ST-01 | HTS-011 | Stretch §2.10 | TODO |
| HTS-038 | FE | Password reset flow (frontend) | ST-01 | HTS-037 | Stretch §2.10 | TODO |
| HTS-039 | BE | Edit/delete own comments (backend) | ST-02 | HTS-023 | Stretch §2.10 | TODO |
| HTS-040 | FE | Edit/delete own comments (frontend) | ST-02 | HTS-039 | Stretch §2.10 | TODO |
| HTS-041 | BE | Ticket activity history (backend) | ST-03 | HTS-019 | Stretch §2.10 | TODO |
| HTS-042 | FE | Ticket activity history (frontend) | ST-03 | HTS-041 | Stretch §2.10 | TODO |
| HTS-043 | FE | Virtualized board rendering for large boards | ST-04 | HTS-026 | Stretch §2.10; FR-B10 | TODO |

### EP-10 Improvements & Tech Debt (optional, non-blocking — found during build)
| ID | Type | Title | Story | Deps | Traceability | Status |
|----|------|-------|-------|------|--------------|--------|
| HTS-044 | BE | Trigram (pg_trgm GIN) index for title substring search | ST-01 | HTS-029 | FR-B9,B10; AMB-4,AMB-10 | DONE |
| HTS-045 | BE | Single-source modified_at (clock-driven, drop dual write) | ST-02 | HTS-019 | AMB-3; FR-K4,P5 | TODO |
| HTS-046 | BE | Absolute session lifetime cap (8h), independent of idle timeout | ST-03 | HTS-011 | AMB-7; NFR-1 | DONE |

---

## Suggested build order

1. **EP-01** (foundation must come first).
2. **EP-02** (auth gates everything else — FR-A12).
3. **EP-03 → EP-04 → EP-05 → EP-06** (data model bottom-up: team → epic → ticket → comment).
4. **EP-07** (board depends on tickets).
5. **EP-08** (error model/security/docs/DoD; some can run in parallel from the start).
6. **EP-09** (only if time allows).
7. **EP-10** (optional; perf/tech-debt found during build — pick up only after mandatory scope is green).

Within a story, build **BE before FE** (FE depends on the BE contract).
