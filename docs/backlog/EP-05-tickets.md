# EP-05 — Tickets

**Goal.** Full ticket lifecycle: create, view all fields, edit (type/team/epic/title/body/state),
and delete (with confirmation, cascading comments). Enforce enum/reference validation server-side,
correct `modified_at` semantics, and the ticket↔epic same-team rule.

**Architecture references:** §6 (Ticket entity + fields/limits + modified-at rule), §8 (endpoints).

## Stories & tickets

| Story | Ticket | Type | Title |
|-------|--------|------|-------|
| ST-01 Ticket CRUD | HTS-019 | BE | Ticket CRUD (fields, enum validation, modified-at semantics, cascade comments) |
| ST-01 Ticket CRUD | HTS-020 | FE | Ticket create/edit/details view |
| ST-02 References & team-change | HTS-021 | BE | Epic-same-team enforcement + team-change epic reset |
| ST-02 References & team-change | HTS-022 | FE | Team/epic dropdown linkage |

**Exit criteria:** verified users can manage tickets with all fields; backend rejects invalid
enums and cross-team epics; `modified_at` advances only on real changes; deleting a ticket
deletes its comments (DoD-3).
