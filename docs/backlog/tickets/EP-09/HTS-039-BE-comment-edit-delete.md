# HTS-039 — [BE] Edit/delete own comments (backend)

| Field | Value |
|-------|-------|
| **ID** | HTS-039 |
| **Type** | BE |
| **Epic** | EP-09 Stretch |
| **Story** | ST-02 Comment edit/delete |
| **Status** | TODO |
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
- [ ] AC-1 — Author can edit their comment (non-empty); body updates, `edited_at` set.
- [ ] AC-2 — Author can delete their comment.
- [ ] AC-3 — A non-author editing/deleting → 403.
- [ ] AC-4 — Editing a comment does not change the ticket's `modified_at`.

## Test plan
**Unit (JUnit + Mockito):** positive (author edit/delete), negative (non-author 403, blank body), boundary (body at max length; deleting an already-deleted comment → 404).
**Integration (Testcontainers — Postgres):** author edits/deletes; another user blocked; ticket modified_at unchanged after edit.

## How to run / verify
```bash
cd backend && ./mvnw test -Dtest='*CommentEdit*'
```

## Definition of Done
- [ ] AC-1..AC-4 met; unit + integration tests pass (positive/negative/boundary)
- [ ] Immutability relaxation noted in analysis/architecture; INDEX.md updated
