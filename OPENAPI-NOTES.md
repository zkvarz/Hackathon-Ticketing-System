# OpenAPI / springdoc + generated frontend types — how it all works

> Personal notes (not committed). Explains the OpenAPI work: **HTS-050** (code-first spec +
> generated FE types), **HTS-051** (CI drift check), **HTS-052** (nginx proxy for the docs).

---

## 1. The mental model: OpenAPI is *not* an alternative to RestControllers

They're different layers:

- **RestControllers** = the *implementation* (Java that handles requests).
- **OpenAPI** = a *specification format* (a language-agnostic JSON/YAML document describing the
  endpoints, params, schemas, status codes).

You still have controllers. The question is only how the OpenAPI document relates to them. Three
approaches:

| Approach | You write | Relationship | Source of truth |
|----------|-----------|--------------|-----------------|
| No OpenAPI (before) | Controllers only | — | The code |
| **Code-first (chosen)** | Controllers (+ light annotations) | Spec is **generated from** the code at runtime | The code |
| Contract-first | `openapi.yaml` first | Interfaces/DTOs are **generated from** the spec | The YAML |

We chose **code-first** (springdoc): near-zero ceremony, live docs, and a spec good enough to
generate the frontend types — which is the real win, because `frontend/src/api/*.ts` used to
hand-copy the backend DTO shapes and could drift. Contract-first would be over-engineering for a
solo, internal, small API.

---

## 2. Code-first: the controllers *are* the spec

No YAML was written. springdoc reads the annotations already on the controllers. This existing code:

```java
// TicketController.java
@RestController
@RequestMapping("/api/tickets")
public class TicketController {
    @GetMapping("/{id}")
    public TicketResponse get(@PathVariable UUID id) { ... }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse create(@Valid @RequestBody TicketRequest request, ...) { ... }
}
```

…becomes this, served live at `/v3/api-docs`, with **zero** controller changes:

```json
"/api/tickets/{id}": { "get": { "operationId": "get",
  "responses": { "200": { "content": { "application/json":
    { "schema": { "$ref": "#/components/schemas/TicketResponse" } } } } } } }
```

---

## 3. Making the spec *accurate* — the only annotations added

springdoc infers most things; two it can't:

### (a) Nullability
A Java `String`/`UUID` field looks non-null to the scanner. `epicId` can be null, so we told the spec:

```java
// TicketResponse.java
@Schema(nullable = true) UUID epicId,
@Schema(nullable = true) String epicTitle,
```
→ `"epicId": { "type": "string", "format": "uuid", "nullable": true }`

Same for `EpicResponse.description`, `CommentResponse.editedAt`,
`TicketActivityResponse.oldValue/newValue`.

### (b) The standardized error model
`ApiError` is only ever produced by the `@RestControllerAdvice`, never returned by a controller — so
the scanner never sees it. `OpenApiConfig` registers it and attaches it to every operation:

```java
// OpenApiConfig.java  (the OpenApiCustomizer)
Map<String, Schema> errorSchemas = ModelConverters.getInstance().readAll(ApiError.class);
errorSchemas.forEach(components::addSchemas);            // ApiError + FieldError now in the spec
...
addError(responses, "409", "Conflict (e.g. NAME_TAKEN, TEAM_HAS_CHILDREN)", errorContent);
```

It also marks response-schema properties `required` (a record always serializes every field), so
generated clients treat them as always-present while `nullable` governs whether the value may be null.

---

## 4. The payoff: frontend types generated from the spec

`npm run gen:api` turns the spec into `src/api/schema.d.ts`. The API modules changed from
hand-copied shapes to derived-from-spec:

```ts
// tickets.ts — BEFORE (hand-written, could drift)
export type TicketType = 'bug' | 'feature' | 'fix';
export interface Ticket {
  id: string; teamId: string; epicId: string | null; /* …12 fields by hand… */
}

// tickets.ts — AFTER (single source of truth = the backend spec)
import type { components } from './schema';
export type Ticket = components['schemas']['TicketResponse'];
export type TicketType = Ticket['type'];   // "bug" | "feature" | "fix", straight from @JsonValue
```

Derived: `Ticket`, `Team`, `Epic`, `Comment`, `TicketActivity`, `UserResponse`, `ApiErrorBody`,
`FieldError`, `HealthStatus`, and the ticket enums. **Request-input** types (e.g. `TicketInput`)
stay hand-written — the client decides how an absent field is sent (`null` vs omitted).

> `schema.d.ts` is **types only** — no runtime code, nothing ships in the bundle. It's a
> compile-time safety net, so it cannot break the running app.

---

## 5. `gen:api` demystified — and how it works in CI

The one command does exactly two things:

```jsonc
// package.json
"gen:api": "openapi-typescript http://localhost:8080/v3/api-docs -o src/api/schema.d.ts"
```
1. **HTTP GET** that URL (the live JSON spec).
2. **Write** the TypeScript file.

So it needs a **running backend**. That's the bit that's confusing about CI — there's no dev server
sitting there, so **CI starts the backend itself first**, then runs the same command.

### Local flow (what you do)
```
backend running (:8080) ──GET /v3/api-docs──▶ openapi-typescript ──writes──▶ src/api/schema.d.ts
                                                                              (you commit it)
```

### CI flow — the `api-contract` job (HTS-051)
```yaml
- run: cp .env.example .env
- run: docker compose up --build -d backend      # ← CI boots the backend (pulls in db + mailpit)
- run: timeout 180 bash -c 'until curl -sf http://localhost:8080/api/health | grep -q UP; do sleep 3; done'
- run: npm ci                                     # (working-directory: frontend)
- run: npm run gen:api                            # ← same GET → writes schema.d.ts vs the LIVE backend
- run: git diff --exit-code -- src/api/schema.d.ts   # ← changed vs what's committed?
- run: npm run typecheck                          # ← does the SPA still compile against the contract?
```

Step by step:
1. **CI builds & boots the backend** from the repo's current code.
2. Backend serves the spec at `/v3/api-docs` (dev profile).
3. **`gen:api` fetches that live spec** and overwrites `schema.d.ts` on the runner.
4. **Two gates decide pass/fail:**

| Check | Catches | How it fails |
|-------|---------|--------------|
| `git diff --exit-code` | Backend contract changed but nobody re-ran `gen:api` + committed | Regenerated file differs from the committed one → non-zero exit + a `::error::` hint |
| `npm run typecheck` | Contract changed in a way the SPA code doesn't handle (e.g. a used field was removed) | `tsc` errors against the freshly-regenerated types |

Together they guarantee the committed types **match the real backend** *and* the **SPA compiles**
against them. Drift can't merge.

### Why commit `schema.d.ts` at all, then?
So normal `npm run build` (and the `frontend` CI job) **don't** need a backend — they consume the
committed file. The `api-contract` job is the one place that spins up the backend to confirm that
committed file is still honest.

### Why a dedicated job (not folded into `e2e`)
It's cheaper — boots only the backend (no frontend build, no Playwright, no chromium) and runs in
parallel, so it doesn't lengthen the critical path. It also reads clearly as "the API contract is in
sync" rather than mixing with "E2E broke."

---

## 6. Dev-only, respecting the security posture

```java
// SecurityConfig.java
if (apiDocsEnabled) {   // = ${springdoc.api-docs.enabled}, true in dev
    auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
}
```
```yaml
# application-prod.yml
springdoc:
  api-docs: { enabled: false }
  swagger-ui: { enabled: false }
```
In prod both **404** *and* fall off the allowlist — belt and suspenders. The nginx proxy (below)
then just forwards to that 404, so it doesn't widen the prod surface.

---

## 7. The docs URLs (nginx proxy, HTS-052)

`frontend/nginx.conf` proxies `/swagger-ui` and `/v3/api-docs` to `backend:8080`, so the docs are on
the same origin as the app:

| | App origin (proxied) | Direct (backend) |
|---|----------------------|------------------|
| Swagger UI | http://localhost:8081/swagger-ui/index.html | http://localhost:8080/swagger-ui/index.html |
| Spec JSON | http://localhost:8081/v3/api-docs | http://localhost:8080/v3/api-docs |

---

## 8. Day-to-day / cheat sheet

```bash
# Browse the API interactively (dev):
open http://localhost:8081/swagger-ui/index.html

# After ANY backend API change:
cd frontend
npm run gen:api        # regenerate types from the live spec
npm run typecheck      # tells you what in the SPA needs updating
git add src/api/schema.d.ts && git commit   # commit the regenerated types

# CI (api-contract job) will reject a PR whose committed types are stale or don't typecheck.
```

---

## 9. Files touched (for reference)

**Backend (HTS-050):**
- `pom.xml` — `springdoc-openapi-starter-webmvc-ui:2.6.0`
- `config/OpenApiConfig.java` — API info + `OpenApiCustomizer` (register ApiError, common 4xx, mark required)
- `config/SecurityConfig.java` — allowlist docs paths only when enabled
- `**/dto/*Response.java` — `@Schema(nullable = true)` on nullable fields
- `resources/application.yml` — OpenAPI 3.0 output + `paths-to-match=/api/**`
- `resources/application-prod.yml` — disable api-docs + swagger-ui
- `test/**/OpenApiDocsIntegrationTest.java` — endpoint is public + documents the contract

**Frontend (HTS-050 / HTS-052):**
- `package.json` — `openapi-typescript` devDep + `gen:api` script
- `src/api/schema.d.ts` — generated, committed
- `src/api/{types,tickets,teams,epics,comments,activity}.ts` — derive response types from the spec
- `nginx.conf` — proxy `/swagger-ui` + `/v3/api-docs`

**CI (HTS-051):**
- `.github/workflows/ci.yml` — `api-contract` job

**Docs:** `docs/architecture.md` (§8, §11), `README.md` (API documentation section),
`docs/backlog/INDEX.md`, `docs/backlog/tickets/EP-10/HTS-050..052-*.md`.
