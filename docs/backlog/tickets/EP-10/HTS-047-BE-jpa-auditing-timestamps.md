# HTS-047 — [BE] Single-source timestamps via Spring Data JPA Auditing (Clock-driven)

| Field | Value |
|-------|-------|
| **ID** | HTS-047 |
| **Type** | BE (tech-debt) |
| **Epic** | EP-10 Improvements & Tech Debt |
| **Story** | ST-02 Timestamp integrity |
| **Status** | TODO |
| **Depends on** | HTS-019 |
| **Blocks** | — |
| **Supersedes** | HTS-045 (same goal; this ticket pins the concrete approach) |
| **Traceability** | AMB-3; FR-K4, FR-P5; architecture.md §6, §7 |

## Goal
Make `created_at` / `modified_at` come from **one authoritative, clock-driven mechanism** for every
entity, using Spring Data JPA Auditing, so the value that is persisted is exactly the value the
service computed and returned — and so it is deterministic under a fixed `Clock`.

## The real reason for the change (why)
Today `modified_at` has **two writers that disagree in value**:

1. `BaseEntity.@PreUpdate` sets `Instant.now()` at *flush* time (used by Team/Epic and as a
   backstop for everything).
2. `Ticket` (via `TicketService`) sets it from the injected `Clock` on a real change / state
   change — `applyChanges(...)` and `changeState(...)` call `markModified(clock.instant())`.

Because the JPA flush callback runs **last**, in production `@PreUpdate`'s `Instant.now()`
**overwrites** the clock value. The concrete consequences:

- **Response ≠ database.** The `modified_at` returned in the create/update/PATCH response (the
  clock value on the in-memory entity) is **not** the instant actually stored (the flush-time
  `now()`). A client that reads the row back sees a *different* `modified_at` than the mutating
  call returned — an observable inconsistency, differing by the flush delay.
- **Dead code in prod.** The service-layer clock write is effectively dead: it only affects the
  transient response object and is discarded on flush. The injected `Clock` (added precisely for
  correctness/testability) does not actually determine the persisted value.
- **Not deterministic / not testable.** Team and Epic timestamps always come from `Instant.now()`,
  so they cannot be asserted against a fixed test `Clock` and do not honour an injected clock at
  all — inconsistent with the ticket path and with FR-P5 (server-set UTC timestamps).

> This is a **correctness/clarity/determinism smell, not a user-visible bug.** The app's claimed
> behaviour (AMB-3: `modified_at` advances on a real change; HTS-027: a state PATCH always
> advances it) is already satisfied today. This ticket removes the disagreement and the dead write.

## What behavior it fixes
- **Persisted == returned:** the stored `modified_at` equals the instant the service computed and
  handed back in the response (single source of truth).
- **Deterministic timestamps** across Team / Epic / Ticket under a fixed `Clock` (testability; FR-P5).
- **One writer, no dead code:** the redundant/overwritten assignment is gone.
- **Preserved semantics:** AMB-3 (advance only on a *real* change) and the documented HTS-027
  exception (a state PATCH advances `modified_at` even when the target state equals the current
  one) both still hold.

## Scope
- In scope: convert `BaseEntity` to Spring Data JPA Auditing driven by the app `Clock`; remove the
  hand-written `@PreUpdate`/`@PrePersist` timestamp writes (keep `@PrePersist` for UUIDv7 id
  generation); adjust `Ticket`/`TicketService` so the state-PATCH still always advances; verify
  Team/Epic advance on real change and not on a no-op.
- Out of scope: changing AMB-3 semantics; changing the HTS-027 "always advance on state PATCH"
  behaviour; touching entities that do not expose timestamps beyond what auditing gives for free.

## Technical approach
- Add `@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")` and a bean
  `DateTimeProvider auditingDateTimeProvider(Clock clock)` returning `Optional.of(clock.instant())`
  — so **both** timestamps are sourced from the injected `Clock` (contamination-free, per Spring
  context; no static state).
- `BaseEntity`: `@EntityListeners(AuditingEntityListener.class)`; `createdAt` → `@CreatedDate`,
  `modifiedAt` → `@LastModifiedDate`. Keep `@PrePersist` **only** to assign the UUIDv7 id. Remove
  `@PreUpdate` and the `markModified(...)` dual write.
- **AMB-3** falls out naturally: `@LastModifiedDate` fires on the JPA update callback, which only
  runs when Hibernate dirty-checking detects a real change — a no-op edit issues no UPDATE, so no
  bump. `Ticket.applyChanges(...)` keeps mutating only changed fields.
- **HTS-027 state-PATCH always-advance:** a same-state PATCH does not dirty the row, so auditing
  alone would not bump it. Preserve the behaviour with an explicit forced-dirty trigger in
  `changeState(...)` (e.g. re-stamp via the same `Clock` to force the UPDATE; auditing then writes
  the authoritative value from the same clock, so there is no *disagreeing* second source).
  Document this single intentional trigger.
- Update `TicketService` to drop the now-redundant `clock.instant()` argument threading where it is
  no longer needed.

## Acceptance criteria
- [ ] AC-1 — Timestamps are written by a single mechanism (JPA Auditing via the `Clock`
  `DateTimeProvider`); no disagreeing/redundant assignment remains.
- [ ] AC-2 — AMB-3 preserved for **all** of Team/Epic/Ticket: a real change advances `modified_at`,
  a no-op save does not.
- [ ] AC-3 — HTS-027 preserved: a state PATCH advances `modified_at` even when the state is
  unchanged.
- [ ] AC-4 — With a fixed `Clock`, the **persisted** `modified_at` equals the **returned**
  `modified_at` equals the clock instant (create and update), for Team/Epic/Ticket.

## Test plan
**Unit (JUnit 5 + Mockito):** fixed-clock assertions on create + real change + no-op across
Team/Epic/Ticket; state-PATCH-on-same-state still advances.

**Integration (Testcontainers — Postgres):** round-trip a create and an update; assert the
persisted `modified_at` equals the value the API returned (persisted == returned == clock instant),
advanced only on real change, and always on a state PATCH.

## How to run / verify
```bash
cd backend && ./mvnw test
```

## Definition of Done
- [ ] AC-1..AC-4 met
- [ ] Existing Team/Epic/Ticket timestamp tests still pass (or updated intentionally)
- [ ] architecture.md §6 note on timestamp sourcing updated to describe JPA Auditing
- [ ] HTS-045 marked superseded; INDEX.md updated
