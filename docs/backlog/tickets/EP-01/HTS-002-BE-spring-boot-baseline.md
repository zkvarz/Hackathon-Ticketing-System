# HTS-002 — [BE] Spring Boot baseline: config, Postgres, Flyway baseline, health endpoint, Dockerfile

| Field | Value |
|-------|-------|
| **ID** | HTS-002 |
| **Type** | BE |
| **Epic** | EP-01 Foundation |
| **Story** | ST-02 Backend baseline |
| **Status** | TODO |
| **Depends on** | HTS-001 |
| **Blocks** | HTS-005, HTS-031, HTS-004 |
| **Traceability** | architecture.md §3, §7, §8; FR-P5, FR-P8, FR-P9; DoD-9 |

## Goal
Create a runnable Spring Boot application that connects to Postgres, applies a Flyway baseline
migration, exposes a public health endpoint, and packages into a container — the foundation
all backend features build on.

## Scope
- In scope: Maven project (`pom.xml`, Java 21), `TicketsApplication`, `application.yml` reading
  env (architecture.md §5), datasource to `db`, Flyway with a baseline migration
  (`V1__baseline.sql` — extensions/UUID support, no app tables yet or minimal scaffolding),
  `GET /api/health` (public), multi-stage `Dockerfile`, base `common/` package (error model
  placeholder, UUIDv7 generator, base entity with id + timestamps), Testcontainers test setup.
- Out of scope: business entities/endpoints (later epics); full error model (HTS-031);
  security config (HTS-013) — baseline leaves endpoints open except where noted.

## Technical approach
- Spring Web + Data JPA + Flyway + Mail + Validation starters; Postgres driver.
- `application.yml`: datasource URL/user/pass from env; `spring.jpa.hibernate.ddl-auto=validate`
  (schema owned by Flyway, FR-P8); UTC timezone (FR-P5 / architecture.md §6).
- UUIDv7 generation strategy in `common/` (custom generator or library) — A-5.
- Health endpoint `GET /api/health` → `{status:"UP"}`; remains public (FR-A12 allowance).
- Multi-stage Dockerfile: Maven build → slim JRE runtime.

## Acceptance criteria
- [ ] AC-1 — App boots in the `backend` container and connects to Postgres.
- [ ] AC-2 — Flyway applies the baseline migration on startup; re-running is idempotent.
- [ ] AC-3 — `GET /api/health` returns 200 `{status:"UP"}`.
- [ ] AC-4 — Fresh DB contains only schema + Flyway metadata, no application rows (DoD-9).
- [ ] AC-5 — `ddl-auto=validate`: app fails fast if schema and entities diverge.

## Test plan
**Unit (JUnit 5 + Mockito):**
- Positive: health controller returns UP.
- Negative: UUIDv7 generator rejects/handles null input appropriately; config binding fails with missing required props.
- Boundary: UUIDv7 monotonicity/ordering sanity (two sequential IDs are time-ordered).

**Integration (Testcontainers — Postgres):**
- Spin up Postgres container; boot context; assert Flyway baseline applied and `flyway_schema_history` present.
- Assert no application tables contain rows (empty DB, DoD-9).
- `GET /api/health` via MockMvc/RestAssured returns 200.

## How to run / verify
```bash
cd backend
./mvnw test                          # unit + Testcontainers integration (Docker required)
docker compose up --build backend db # app boots, health green
curl http://localhost:8080/api/health
```

## Definition of Done
- [ ] AC-1..AC-5 met
- [ ] Unit tests (positive/negative/boundary) pass
- [ ] Testcontainers integration test passes (real Postgres)
- [ ] Container builds and boots via compose; health green
- [ ] INDEX.md status updated
