# HTS-040 — [FE] Edit/delete own comments (frontend)

| Field | Value |
|-------|-------|
| **ID** | HTS-040 |
| **Type** | FE |
| **Epic** | EP-09 Stretch |
| **Story** | ST-02 Comment edit/delete |
| **Status** | DONE |
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
- [x] AC-1 — Edit/delete controls appear only on the user's own comments.
- [x] AC-2 — Editing saves a non-empty body and shows an "edited" indicator.
- [x] AC-3 — Delete confirms then removes the comment.
- [x] AC-4 — A 403 (not author) is handled gracefully (shouldn't normally occur).

## Test plan
**Component (Vitest + RTL):** positive (edit/delete own), negative (controls absent on others', blank edit blocked), boundary (cancel edit restores original).
**API-contract (MSW):** mock PUT/DELETE success + 403.

## How to run / verify
```bash
cd frontend && npm test -- comment-edit
```

## Definition of Done
- [x] AC-1..AC-4 met; component + MSW tests pass (positive/negative/boundary)
- [x] INDEX.md status updated

## Implementation notes (as built)
- `api/comments.ts`: `Comment` gains `editedAt: string | null`; `editComment(ticketId, commentId,
  body)` → `PUT`, `deleteComment(ticketId, commentId)` → `DELETE` (the nested paths from HTS-039).
- `CommentsPanel` takes an optional `currentUserId` prop; edit/delete controls render only when
  `comment.authorId === currentUserId`. Inline edit (textarea seeded with the body, Save disabled
  when blank, Cancel restores), delete-with-confirm, and an "(edited)" indicator when `editedAt` is
  set. Edit/delete mutations invalidate only `['comments', ticketId]` (never the ticket/board —
  FR-C5). A 403 is surfaced as an inline error (AC-4).
- Ownership source: `TicketDetailsPage` passes `currentUserId={currentUser?.id}` via a new
  non-throwing `useCurrentUser()` hook (returns null without an `AuthProvider`, so isolated
  component tests — which pass the id directly — don't need the provider and existing panel tests
  are unaffected).
- Tests (`comment-edit.test.tsx`, 6): own-only controls, edit + "edited" indicator, blank-edit
  blocked, cancel-restores, confirm+delete removes, 403 handled. Full FE suite green (102);
  typecheck clean.
