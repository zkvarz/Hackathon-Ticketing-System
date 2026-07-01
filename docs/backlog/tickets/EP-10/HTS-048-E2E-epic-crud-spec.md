# HTS-048 — [QA/E2E] Explicit epic-CRUD Playwright spec

| Field | Value |
|-------|-------|
| **ID** | HTS-048 |
| **Type** | QA/E2E |
| **Epic** | EP-10 Improvements & Tech Debt |
| **Story** | ST-05 E2E (should-have) |
| **Status** | DONE |
| **Depends on** | HTS-036, HTS-018 |
| **Blocks** | — |
| **Priority** | Should-have (non-blocking) |
| **Traceability** | DoD-2; FR-E1..E5, FR-E8; FR-S8; architecture.md §12 |

## Goal
The HTS-036 E2E suite covers epics only indirectly (a ticket references an epic). Add a dedicated
browser spec that exercises the epic management screen end-to-end: create, edit, and delete an
epic, plus the delete-when-referenced guard — so **DoD-2** ("teams & epics managed via UI +
persist") is asserted directly rather than inferred.

## Scope
- In scope: one Playwright spec (`epics.spec.ts`) that, using the shared authenticated session and
  its own team, walks: create epic → verify it lists → edit its title → delete an empty epic →
  confirm a referenced epic (one with a ticket) cannot be deleted (Delete disabled).
- Out of scope: backend/component coverage (already exists: `EpicCrudIntegrationTest`,
  `EpicsPage.test.tsx`); new helpers beyond a small `createEpic` UI helper.

## Technical approach
- Reuse `frontend/e2e/helpers.ts` (`createTeam`, `createTicket`, `uniqueName`). Add a `createEpic`
  helper that drives the `/epics` screen (team selector → "New epic" title → "Add epic").
- Select the spec's own team in the team selector before creating/reading epics so the assertions
  are isolated from other specs' data.
- Role/label/text selectors and Playwright auto-waiting only — no fixed sleeps (consistent with the
  rest of the suite).

## Acceptance criteria
- [x] AC-1 — Create: a new epic appears in the team's epic table.
- [x] AC-2 — Edit: renaming an epic persists (the new title shows; reload-safe via query refetch).
- [x] AC-3 — Delete: an empty epic can be deleted (row disappears).
- [x] AC-4 — Referenced guard: an epic with a ticket has its Delete disabled (FR-E8 / DoD-2).

## Test plan
The spec *is* the test. Self-contained: creates its own team, epics, and (for AC-4) a ticket that
references an epic. Runs green against the composed stack alongside the existing four specs.

## How to run / verify
```bash
docker compose up -d            # podman: build images then `podman compose up -d`
cd frontend && npx playwright test e2e/epics.spec.ts
```

## Definition of Done
- [x] `epics.spec.ts` added and green against the composed stack
- [x] `createEpic` helper added to `e2e/helpers.ts`
- [x] Full suite still green (`npm run e2e`) — **6/6 passed** (2026-07-01)
- [x] README E2E blurb + INDEX.md updated

## Implementation notes (as built)
- **`e2e/epics.spec.ts`** (1 spec, DoD-2): create → edit title → delete-empty → referenced-guard,
  all through the `/epics` screen with the shared session and its own team. Role/label selectors,
  Playwright auto-waiting, no sleeps.
- **`e2e/helpers.ts`:** added `createEpic(page, {team, title, description?})` (drives team selector →
  "New epic" → "Add epic"); extended `createTicket` with an optional `epic` (title) that selects the
  ticket form's Epic dropdown, so the guard can attach a ticket to the epic. Backwards-compatible —
  existing specs pass `createTicket` without `epic`.
- **Verified:** `npx playwright test` → **6/6 green** (~19s), the new spec runs in ~3.2s.
- Selector notes for maintainers: the epic row is matched by `getByRole('row', { name: /title/ })`;
  the inline edit input is `getByLabel('Edit title of <title>')`; delete is a two-step Delete →
  Confirm inside the row; the referenced epic's Delete is `toBeDisabled()` once a ticket references
  it (`ticketCount > 0`).
