# EP-07 — Kanban Board

**Goal.** The primary screen: a five-column Kanban board for one selected team, with draggable
cards that persist state changes immediately (reverting on failure), most-recently-modified
ordering within columns, and filter/search (type, epic, title substring).

**Architecture references:** §6 (Ticket/state), §8 (board query + state endpoint), §11
(board UI + dnd-kit + revert-on-failure).

## Stories & tickets

| Story | Ticket | Type | Title |
|-------|--------|------|-------|
| ST-01 Board view | HTS-025 | BE | Board query (by team, 5 states, modified-desc order) |
| ST-01 Board view | HTS-026 | FE | Board UI (columns, cards, team selector, counts) |
| ST-02 Drag-and-drop | HTS-027 | BE | State-change endpoint (validate, persist, advance modified-at) |
| ST-02 Drag-and-drop | HTS-028 | FE | Drag-drop (dnd-kit) + optimistic move + revert-on-failure |
| ST-03 Filters & search | HTS-029 | BE | Server-side filter (type, epic) + title search (AND) |
| ST-03 Filters & search | HTS-030 | FE | Filter/search controls + wiring |

**Exit criteria:** board shows tickets in correct state columns for the selected team; drag
persists and survives refresh (reverting + erroring on failure); filters/search narrow the board;
usable with ≥100 tickets (DoD-5, DoD-6).
