# HTS-045 — [BE] Single-source `modified_at` (clock-driven, drop the dual write)

> **Superseded by [HTS-047](HTS-047-BE-jpa-auditing-timestamps.md).** This ticket captured the
> problem with two candidate approaches (A/B). The chosen implementation — Spring Data JPA
> Auditing driven by the app `Clock` — plus the concrete rationale ("the real reason") and the
> exact behaviour it fixes are specified in HTS-047. Kept here for history; do not implement from
> this ticket.

| Field | Value |
|-------|-------|
| **ID** | HTS-045 |
| **Type** | BE (tech-debt) |
| **Epic** | EP-10 Improvements & Tech Debt |
| **Story** | ST-02 Timestamp integrity |
| **Status** | SUPERSEDED (→ HTS-047) |
| **Depends on** | HTS-019 |
| **Blocks** | — |
| **Traceability** | AMB-3; FR-K4, FR-P5; architecture.md §6 |

## Goal
Make `modified_at` come from exactly one authoritative source so its value is deterministic and
the code has no redundant write.

## Context / why
Today `modified_at` is written in two places that happen to agree behaviorally but not in value:
- `BaseEntity.@PreUpdate` sets `Instant.now()` at flush time (used by Team/Epic and as a
  backstop);
- the ticket service/entity set it from the injected `Clock` on a real change / state change
  (so unit tests are deterministic and the returned entity is immediately consistent).

The AMB-3 rule ("advance only on a real change") is satisfied, but in production the flush-time
`@PreUpdate` value overwrites the clock value, so the clock write is effectively dead in prod
and the stored instant isn't the one the service computed. This is a correctness/clarity smell,
not a bug.

## Scope
- In scope: pick one authority. Preferred: make timestamps fully **clock-driven in the service
  layer** (inject `Clock` where entities are mutated) and remove the `@PreUpdate` now() write —
  or, alternatively, make `BaseEntity` clock-aware so the callback and the service agree.
  Update Team/Epic/Ticket consistently so all three behave identically.
- Out of scope: changing AMB-3 semantics; the state-change "always advance even on same state"
  behavior (that stays, it is intentional — see HTS-027).

## Technical approach
- Option A (preferred): remove modified-at management from `@PreUpdate`; have each service set
  `created_at`/`modified_at` from the injected `Clock` at create and on real change. Keep
  `@PrePersist` for id generation. Verify Team/Epic rename + Ticket edit still advance
  correctly and no-ops still don't.
- Option B: keep `@PreUpdate` but source its instant from a clock holder, so tests can fix it
  and the value matches the service. More invasive to entity/base wiring.
- Whichever: one source of truth, no double write.

## Acceptance criteria
- [ ] AC-1 — `modified_at` is written by a single mechanism; no redundant assignment remains.
- [ ] AC-2 — AMB-3 preserved: real change advances it, a no-op save does not (all of Team/Epic/Ticket).
- [ ] AC-3 — With a fixed clock, the stored/returned `modified_at` equals the clock instant (deterministic).

## Test plan
**Unit (JUnit 5 + Mockito):**
- Fixed-clock assertions on create + real change + no-op across Team/Epic/Ticket.

**Integration (Testcontainers — Postgres):**
- Round-trip a real update and assert the persisted `modified_at` is the intended instant (UTC), advanced only on real change.

## How to run / verify
```bash
cd backend && ./mvnw test
```

## Definition of Done
- [ ] AC-1..AC-3 met
- [ ] Existing Team/Epic/Ticket timestamp tests still pass (or updated intentionally)
- [ ] architecture.md §6 note on timestamp sourcing updated if the mechanism changes
- [ ] INDEX.md status updated
