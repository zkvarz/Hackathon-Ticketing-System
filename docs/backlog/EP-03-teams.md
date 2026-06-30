# EP-03 — Teams

**Goal.** CRUD for teams with non-empty, case-insensitively-unique names, and a delete that is
blocked (HTTP 409) while the team still contains tickets or epics.

**Architecture references:** §6 (Team entity + constraints), §8 (endpoints + error model).

## Stories & tickets

| Story | Ticket | Type | Title |
|-------|--------|------|-------|
| ST-01 Team management | HTS-015 | BE | Team CRUD (validation, CI-unique, 409 on referenced delete) |
| ST-01 Team management | HTS-016 | FE | Team management screen |

**Exit criteria:** verified users can list/create/rename/delete teams; duplicate names and
referenced deletes are rejected with clear messages; UI disables delete for referenced teams
(wireframe image4).
