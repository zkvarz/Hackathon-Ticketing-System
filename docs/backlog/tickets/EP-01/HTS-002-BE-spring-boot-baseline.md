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
| **Status** | DONE |
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
- [x] AC-1 — App boots in the `backend` container and connects to Postgres. *(Verified: container boot → `/api/health` 200.)*
- [x] AC-2 — Flyway applies the baseline migration on startup; re-running is idempotent. *(Verified: `flyway_schema_history` shows version 1 success.)*
- [x] AC-3 — `GET /api/health` returns 200 `{status:"UP"}`. *(Verified live + via test.)*
- [x] AC-4 — Fresh DB contains only schema + Flyway metadata, no application rows (DoD-9). *(Verified: only `flyway_schema_history` in public schema.)*
- [x] AC-5 — `ddl-auto=validate`: app fails fast if schema and entities diverge. *(Boots clean with no entities mapped to tables.)*

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
- [x] AC-1..AC-5 met
- [x] Unit tests (positive/negative/boundary) pass — `UuidV7Test` (4), `HealthControllerTest` (2)
- [x] Testcontainers integration test passes (real Postgres) — `BaselineIntegrationTest` (3)
- [x] Container builds and boots; health green — image built via `podman build`, booted against Postgres
- [x] INDEX.md status updated
- [x] Committed to main

## Notes
- Mockito's default inline mock maker can't self-attach its agent in this WSL2/Podman
  environment, so tests use the **subclass mock maker** (`src/test/resources/mockito-extensions/`).
  It covers mocking interfaces and non-final Spring components (all this project needs).
- On this Podman machine `docker compose build` hangs (broken `buildx_buildkit_default`
  session). The image was built with `podman build` (buildah) and booted via `podman run`.
  The committed multi-stage Dockerfile is standard and builds on normal Docker/CI; HTS-004
  will verify the compose/build path on a clean runner.
