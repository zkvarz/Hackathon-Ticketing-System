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
- [ ] AC-1 — Loading/empty/error/success components render their respective states.
- [ ] AC-2 — The error-model parser maps `fieldErrors` to fields and `message` to a banner/toast.
- [ ] AC-3 — A thrown render error is caught by the boundary and shows a recoverable fallback.
- [ ] AC-4 — Success and error toasts display and auto-dismiss.

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
- [ ] AC-1..AC-4 met
- [ ] Component tests pass (positive/negative/boundary)
- [ ] Error-model parser matches HTS-031's contract
- [ ] INDEX.md status updated
