# HTS-024 — [FE] Comments panel: list oldest-first, add comment

| Field | Value |
|-------|-------|
| **ID** | HTS-024 |
| **Type** | FE |
| **Epic** | EP-06 Comments |
| **Story** | ST-01 Comments |
| **Status** | DONE |
| **Depends on** | HTS-020, HTS-023 |
| **Blocks** | — |
| **Traceability** | FR-C1, FR-C3, FR-C4; FR-S6; NFR-3; architecture.md §11; wireframe image3 |

## Goal
Add a comments panel to the ticket view: list existing comments oldest-first (author +
timestamp) and a form to post a new non-empty comment.

## Scope
- In scope: comments panel within `/tickets/:id` (per wireframe image3); list oldest-first;
  author + time display; add-comment form with non-empty validation; optimistic or refetch on
  post; loading/empty/error states.
- Out of scope: edit/delete (stretch); backend (HTS-023).

## Technical approach
- Query comments for the ticket; render in order; show author (email per AMB-8) + time.
- Post form disabled while empty/in-flight; on success, clear input and show the new comment.
- Note (UX): posting a comment does not reorder the board (FR-C5) — no board refresh triggered.

## Acceptance criteria
- [x] AC-1 — Existing comments render oldest-first with author and timestamp.
- [x] AC-2 — Posting a non-empty comment adds it to the list.
- [x] AC-3 — Empty/whitespace body is blocked client-side; server 400 also handled.
- [x] AC-4 — Empty state shown when a ticket has no comments.

## Test plan
**Component (Vitest + RTL):**
- Positive: renders ordered comments; submit adds one.
- Negative: empty body blocked; server 400 surfaced.
- Boundary: zero comments → empty state; many comments preserve order.

**API-contract (MSW):**
- Mock list (empty + populated); mock post 201 and 400.

## How to run / verify
```bash
cd frontend && npm test -- CommentsPanel
npm run dev   # open a ticket, scroll to comments
```

## Definition of Done
- [x] AC-1..AC-4 met
- [x] Component + MSW tests pass (positive/negative/boundary)
- [x] Oldest-first ordering + author/time display match wireframe image3
- [x] INDEX.md status updated
