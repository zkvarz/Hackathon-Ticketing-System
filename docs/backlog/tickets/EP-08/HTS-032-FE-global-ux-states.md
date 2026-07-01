# HTS-032 — [FE] Global loading/empty/error/success UX + error boundary/toast

| Field | Value |
|-------|-------|
| **ID** | HTS-032 |
| **Type** | FE |
| **Epic** | EP-08 Cross-cutting & Delivery |
| **Story** | ST-01 Error model & UX states |
| **Status** | TODO |
| **Depends on** | HTS-003 |
| **Blocks** | — |
| **Traceability** | NFR-3; FR-P4 (consumes error model); architecture.md §11 |

## Goal
Provide app-wide UX building blocks for loading, empty, error, and success states, plus a
toast/notification mechanism and a top-level error boundary, consuming the standard error model.

## Scope
- In scope: shared `Loading`/`Empty`/`ErrorState` components (extends HTS-003 primitives); a
  toast/notification system for transient success/error; a React error boundary; a helper that
  maps the backend error model (`code`/`message`/`fieldErrors`) to UI messages.
- Out of scope: per-screen wiring (each feature ticket uses these).

## Technical approach
- Centralized error-model parser turns API errors into user-facing text + field errors.
- Toaster for success/error feedback (e.g. "Comment posted", "Move failed — reverted").
- Error boundary prevents a render error from blanking the app.

## Acceptance criteria
- [x] AC-1 — Loading/empty/error/success components render their respective states.
- [x] AC-2 — The error-model parser maps `fieldErrors` to fields and `message` to a banner/toast.
- [x] AC-3 — A thrown render error is caught by the boundary and shows a recoverable fallback.
- [x] AC-4 — Success and error toasts display and auto-dismiss.

## Test plan
**Component (Vitest + RTL):**
- Positive: each state component renders; toast shows then dismisses.
- Negative: error boundary renders fallback when a child throws.
- Boundary: error model with multiple field errors maps each; error model with only `message` shows a banner.

## How to run / verify
```bash
cd frontend && npm test -- ux-states
```

## Definition of Done
- [x] AC-1..AC-4 met
- [x] Component tests pass (positive/negative/boundary) — `ux-states.test.tsx` (7)
- [x] Error-model parser matches HTS-031's contract (`lib/apiError.ts`; `TicketDetailsPage` now
  builds on it)
- [x] INDEX.md status updated

## Implementation notes (as built)
- `lib/apiError.ts`: `messageOf` / `fieldErrorsOf` / `codeOf` centralize the §8 contract; the
  ticket form's inline errors and save/delete banners now use them (feature-specific
  `EPIC_TEAM_MISMATCH`→epic mapping layered on top).
- `components/toast/ToastProvider.tsx`: `useToast().success/error`, polite/assertive regions,
  auto-dismiss (configurable duration) + manual dismiss.
- `components/ErrorBoundary.tsx`: recoverable fallback with a reset callback; mounted outermost
  in `App` around the Query + Toast providers.
- The pre-existing `Loading`/`Empty`/`ErrorState` primitives (HTS-003) satisfy AC-1.
