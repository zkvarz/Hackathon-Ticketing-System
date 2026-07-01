# HTS-039 — [BE] Edit/delete own comments (backend)

| Field | Value |
|-------|-------|
| **ID** | HTS-039 |
| **Type** | BE |
| **Epic** | EP-09 Stretch |
| **Story** | ST-02 Comment edit/delete |
| **Status** | DONE |
| **Priority** | Optional / stretch |
| **Depends on** | HTS-023 |
| **Blocks** | HTS-040 |
| **Traceability** | Stretch §2.10; relaxes FR-C6; architecture.md §6, §8 |

## Goal
Allow a comment's author to edit or delete their own comment — explicitly relaxing the
mandatory-scope immutability (FR-C6) as a stretch feature.

## Scope
- In scope: `PUT/DELETE /api/comments/{id}` restricted to the author; non-empty body on edit;
  optional `edited_at`; still must not bump the ticket's `modified_at` (consistent with FR-C5).
- Out of scope: FE (HTS-040).

## Technical approach
- Authorization: only the author may edit/delete (the only ownership rule in the app; all other
  data is shared per FR-T6). Others → 403.
- Edit validates non-empty body; record `edited_at`.

## Acceptance criteria
- [x] AC-1 — Author can edit their comment (non-empty); body updates, `edited_at` set.
- [x] AC-2 — Author can delete their comment.
- [x] AC-3 — A non-author editing/deleting → 403.
- [x] AC-4 — Editing a comment does not change the ticket's `modified_at`.

## Test plan
**Unit (JUnit + Mockito):** positive (author edit/delete), negative (non-author 403, blank body), boundary (body at max length; deleting an already-deleted comment → 404).
**Integration (Testcontainers — Postgres):** author edits/deletes; another user blocked; ticket modified_at unchanged after edit.

## How to run / verify
```bash
cd backend && ./mvnw test -Dtest='*CommentEdit*'
```

## Definition of Done
- [x] AC-1..AC-4 met; unit + integration tests pass (positive/negative/boundary)
- [x] Immutability relaxation noted in analysis/architecture; INDEX.md updated

## Implementation notes (as built)
- **Endpoint path:** implemented as nested `PUT`/`DELETE
  /api/tickets/{ticketId}/comments/{commentId}` (not the flat `/api/comments/{id}` the ticket
  sketched) to stay consistent with the existing nested `CommentController` and FE `comments.ts`.
  The service verifies the comment belongs to the path ticket (else 404), keeping the nested
  resource honest.
- `V11__comments_edited_at.sql`: `ALTER TABLE comments ADD COLUMN edited_at TIMESTAMPTZ` (nullable;
  drives the "edited" indicator). `Comment` gains `editedAt` + `edit(body, editedAt)`; the class is
  no longer strictly immutable but still has no `modified_at` and never touches the ticket.
- `CommentService` now injects `Clock`; `update(...)` trims the body and stamps `edited_at` from the
  clock, `delete(...)` removes it. Both go through `requireOwnComment(...)` — load by id, confirm it
  belongs to the ticket (404), confirm the requester is the author by normalized-email match (else
  `CommentAccessDeniedException` → **403 FORBIDDEN**). Neither mutates the ticket, so `modified_at`
  is untouched (FR-C5).
- `CommentResponse` gains `editedAt` (null until first edit).
- Tests: `CommentServiceTest` (+7: author edit stamps/​trims, non-author 403, wrong-ticket 404,
  missing 404, author delete, non-author delete 403, missing delete 404),
  `CommentEditDeleteIntegrationTest` (5: author edit, author delete, non-author 403 on both +
  unchanged, edit doesn't bump ticket modified_at, missing → 404). Full backend suite green (180).
