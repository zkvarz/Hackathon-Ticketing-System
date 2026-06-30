# EP-04 — Epics

**Goal.** CRUD for epics on a dedicated screen. Each epic belongs to exactly one team (fixed at
creation), has a non-empty title and optional description, and cannot be deleted while tickets
reference it (HTTP 409).

**Architecture references:** §6 (Epic entity + constraints), §8 (endpoints).

## Stories & tickets

| Story | Ticket | Type | Title |
|-------|--------|------|-------|
| ST-01 Epic management | HTS-017 | BE | Epic CRUD (team-fixed, title validation, 409 on referenced delete) |
| ST-01 Epic management | HTS-018 | FE | Epic management screen |

**Exit criteria:** verified users can create/list/edit/delete epics per team; team is fixed
after creation; referenced epics cannot be deleted (wireframe image5).
