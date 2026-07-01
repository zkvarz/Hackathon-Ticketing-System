# HTS-050 — [BE/FE] Code-first OpenAPI (springdoc) + generated frontend types

| Field | Value |
|-------|-------|
| **ID** | HTS-050 |
| **Type** | BE/FE |
| **Epic** | EP-10 Improvements & Tech Debt |
| **Story** | ST-06 API contract & docs |
| **Status** | DONE |
| **Depends on** | HTS-031 (error model), HTS-019/023/025 (DTOs) |
| **Blocks** | — |
| **Priority** | Should-have (non-blocking) |
| **Traceability** | NFR-3, NFR-5; architecture.md §8, §11 |

## Goal
Add a machine-readable API contract (OpenAPI 3) generated from the existing controllers, expose
interactive docs (Swagger UI) in dev, and **generate the frontend's API types from that spec** so
the SPA can never silently drift from the backend contract.

## Why (chosen approach: code-first)
Three options were weighed: (1) no OpenAPI — the prior state; (2) **code-first** (springdoc
introspects the controllers, generating the spec at runtime); (3) contract-first (write YAML, codegen
server stubs). Code-first is the sweet spot for a solo, internal, small API: near-zero ceremony,
live docs, and a spec good enough to generate the frontend types — which is the concrete win here,
since `frontend/src/api/*.ts` previously hand-copied the backend DTO shapes and could drift.
Contract-first would be over-engineering.

## Scope
- **BE:** add `springdoc-openapi-starter-webmvc-ui`; serve `/v3/api-docs` + `/swagger-ui` in dev;
  document the standardized `ApiError` model; **disable both in the `prod` profile**; add the docs
  paths to the security allowlist **only while enabled**.
- **FE:** add `openapi-typescript` + a `gen:api` script; generate `src/api/schema.d.ts`; derive the
  response domain types (Ticket/Team/Epic/Comment/TicketActivity/UserResponse/ApiError) and the
  ticket enums from the spec.
- Out of scope: contract-first codegen; generating a runtime API client; deriving request-body types
  (the client owns those).

## Technical approach
- **BE:** `OpenApiConfig` provides the API `Info` and an `OpenApiCustomizer` that registers the
  `ApiError`/`FieldError` schemas (they're produced by the exception handler, never as a return
  type, so the scan can't see them), attaches the common 4xx responses to every operation, and marks
  response-schema properties `required` (records always serialize every field). Nullable response
  fields carry `@Schema(nullable = true)`. `springdoc.api-docs.version=openapi_3_0` keeps `nullable`
  simple for the generator. `SecurityConfig` allowlists `/v3/api-docs/**`, `/swagger-ui/**` gated on
  `${springdoc.api-docs.enabled}`; `application-prod.yml` sets that false.
- **FE:** `npm run gen:api` runs `openapi-typescript http://localhost:8080/v3/api-docs -o
  src/api/schema.d.ts`; the api modules alias `components['schemas'][…]`. Regenerating after a BE
  change surfaces any drift as a `npm run typecheck` failure.

## Acceptance criteria
- [x] AC-1 — `/v3/api-docs` served in dev and reachable **without authentication** (allowlist);
  documents the domain DTOs and the `ApiError` model.
- [x] AC-2 — Swagger UI served at `/swagger-ui/index.html` in dev.
- [x] AC-3 — Both disabled in the `prod` profile (endpoints 404; dropped from the allowlist).
- [x] AC-4 — Frontend types are generated from the spec (`gen:api` → `schema.d.ts`) and the domain
  response types + enums are derived from it; `typecheck`, unit tests, and build stay green.

## Test plan
- **BE (integration, Testcontainers):** `OpenApiDocsIntegrationTest` — unauthenticated GET
  `/v3/api-docs` is 200 and contains `TicketResponse`/`ApiError`/lowercase enum values; Swagger UI
  serves; the spec is written to `target/openapi.json`.
- **FE:** `gen:api` produces `schema.d.ts`; `npm run typecheck` (drift gate) + `npm test` + build.

## How to run / verify
```bash
docker compose up -d           # or podman build + podman compose up -d
curl -s http://localhost:8080/v3/api-docs | head        # the spec (dev only)
open http://localhost:8080/swagger-ui/index.html        # interactive docs (dev only)
cd frontend && npm run gen:api && npm run typecheck      # regenerate + drift check
```

## Definition of Done
- [x] springdoc added; docs served in dev, disabled in prod; security allowlist gated
- [x] `ApiError` documented; response schemas precise (required + nullable)
- [x] `openapi-typescript` + `gen:api`; `schema.d.ts` committed; domain types derived from it
- [x] Backend suite green (193); frontend typecheck + tests (107) + build green
- [x] architecture.md + README updated; INDEX.md updated

## Implementation notes (as built)
- **BE:** `pom.xml` +`springdoc-openapi-starter-webmvc-ui:2.6.0`. New `config/OpenApiConfig`
  (Info + `OpenApiCustomizer`). `@Schema(nullable=true)` on `TicketResponse.epicId/epicTitle`,
  `EpicResponse.description`, `CommentResponse.editedAt`, `TicketActivityResponse.oldValue/newValue`.
  `SecurityConfig.filterChain` takes `@Value("${springdoc.api-docs.enabled:true}")` and permits the
  docs paths only when true. `application.yml` sets `springdoc.api-docs.version=openapi_3_0` +
  `paths-to-match=/api/**`; `application-prod.yml` disables api-docs + swagger-ui. New
  `OpenApiDocsIntegrationTest` (2).
- **FE:** `openapi-typescript` devDep + `gen:api` script; generated `src/api/schema.d.ts` (committed).
  `types.ts`, `tickets.ts`, `teams.ts`, `epics.ts`, `comments.ts`, `activity.ts` now derive their
  response types (and `TicketType`/`TicketState`) from `components['schemas'][…]`. Request-input
  types stay hand-written. Typecheck passing on first regeneration proved there was **no existing
  drift**.
- **Verified live:** rebuilt backend image; `/v3/api-docs` + `/swagger-ui/index.html` return 200
  unauthenticated in dev; `gen:api` against the live URL produced byte-identical output to the
  committed `schema.d.ts`.
- **Note:** Swagger UI is reached directly on the backend port (`:8080/swagger-ui`) — nginx only
  proxies `/api`, not `/v3`/`/swagger-ui`, which is fine for a dev-only aid.
