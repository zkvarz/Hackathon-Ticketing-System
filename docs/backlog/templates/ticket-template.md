# HTS-XXX — [BE|FE|INFRA|DOCS|QA] <Concise Jira-style title>

> Copy this file to `docs/backlog/tickets/EP-NN/HTS-XXX-...md` for each new ticket.

| Field | Value |
|-------|-------|
| **ID** | HTS-XXX |
| **Type** | BE / FE / INFRA / DOCS / QA |
| **Epic** | EP-NN <name> |
| **Story** | ST-NN <name> |
| **Status** | TODO |
| **Depends on** | HTS-… (or —) |
| **Blocks** | HTS-… (or —) |
| **Traceability** | FR-…, NFR-…, DoD-…, AMB-… (+ architecture.md §) |

## Goal
One or two sentences: what capability this ticket delivers and why.

## Scope
- In scope: …
- Out of scope: … (defer to ticket HTS-… / out of project scope per analysis §12)

## Technical approach
Concrete plan: endpoints/classes (BE) or components/hooks (FE), key validation rules,
data touched, references to `architecture.md` sections instead of repeating shared detail.

## Acceptance criteria
- [ ] AC-1 — testable statement (maps to FR-…)
- [ ] AC-2 — …
- [ ] Errors return the standard model with correct HTTP status (BE) / UX state shown (FE)

## Test plan
**Unit (BE: JUnit+Mockito / FE: Vitest+RTL)** — must include positive, negative, boundary:
- Positive: …
- Negative: …
- Boundary: …

**Integration (BE: Testcontainers Postgres[/Mailpit] / FE: MSW API-contract):**
- Scenario: …

## How to run / verify
```bash
# commands to build, test, and manually verify this ticket
```

## Definition of Done
- [ ] Acceptance criteria met
- [ ] Unit tests (positive/negative/boundary) pass
- [ ] Integration test passes (where applicable) using containers
- [ ] No secrets committed; config via env (where applicable)
- [ ] Traceable to its FR/DoD IDs; INDEX.md status updated
