# EP-06 — Comments

**Goal.** Authenticated users can add non-empty comments to a ticket and see them in
chronological (oldest-first) order with author and timestamp. Comments are immutable in the
mandatory scope and adding one does not change the ticket's `modified_at` (and thus not its
board ordering).

**Architecture references:** §6 (Comment entity), §8 (endpoints).

## Stories & tickets

| Story | Ticket | Type | Title |
|-------|--------|------|-------|
| ST-01 Comments | HTS-023 | BE | Comment add + list (chronological, non-empty, immutable, no modified-at bump) |
| ST-01 Comments | HTS-024 | FE | Comments panel (list oldest-first, add comment) |

**Exit criteria:** users can add and read comments with author + timestamp; comments don't
bump ticket modified-at (DoD-4).
