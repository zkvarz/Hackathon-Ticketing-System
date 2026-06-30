# HTS-040 — [FE] Edit/delete own comments (frontend)

| Field | Value |
|-------|-------|
| **ID** | HTS-040 |
| **Type** | FE |
| **Epic** | EP-09 Stretch |
| **Story** | ST-02 Comment edit/delete |
| **Status** | TODO |
| **Priority** | Optional / stretch |
| **Depends on** | HTS-039, HTS-024 |
| **Blocks** | — |
| **Traceability** | Stretch §2.10; NFR-3; architecture.md §11 |

## Goal
Add edit and delete affordances to the author's own comments in the comments panel.

## Scope
- In scope: inline edit + delete (with confirm) shown only on the current user's own comments;
  optimistic update or refetch; "edited" indicator; loading/error states.
- Out of scope: backend (HTS-039).

## Acceptance criteria
- [ ] AC-1 — Edit/delete controls appear only on the user's own comments.
- [ ] AC-2 — Editing saves a non-empty body and shows an "edited" indicator.
- [ ] AC-3 — Delete confirms then removes the comment.
- [ ] AC-4 — A 403 (not author) is handled gracefully (shouldn't normally occur).

## Test plan
**Component (Vitest + RTL):** positive (edit/delete own), negative (controls absent on others', blank edit blocked), boundary (cancel edit restores original).
**API-contract (MSW):** mock PUT/DELETE success + 403.

## How to run / verify
```bash
cd frontend && npm test -- comment-edit
```

## Definition of Done
- [ ] AC-1..AC-4 met; component + MSW tests pass (positive/negative/boundary)
- [ ] INDEX.md status updated
