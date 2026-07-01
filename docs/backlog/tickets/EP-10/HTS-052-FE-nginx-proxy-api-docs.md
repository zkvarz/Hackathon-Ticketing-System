# HTS-052 — [FE/INFRA] Proxy Swagger UI + OpenAPI spec through nginx (app origin)

| Field | Value |
|-------|-------|
| **ID** | HTS-052 |
| **Type** | FE/INFRA |
| **Epic** | EP-10 Improvements & Tech Debt |
| **Story** | ST-06 API contract & docs |
| **Status** | DONE |
| **Depends on** | HTS-050 |
| **Blocks** | — |
| **Priority** | Should-have (non-blocking) |
| **Traceability** | architecture.md §4, §8 |

## Goal
Make the API docs reachable on the same origin as the app (`http://localhost:8081/swagger-ui`,
`/v3/api-docs`), not just on the backend port `:8080`, so the docs live behind the one URL people
already use.

## Why
HTS-050 served the docs directly on the backend (`:8080`) because nginx only proxied `/api`. Routing
them through nginx is a small config addition and gives a single, consistent entry point. The
backend still gates exposure (docs are disabled in the `prod` profile → the proxy just returns 404
there), so this doesn't widen the prod surface.

## Scope
- In scope: add `location /swagger-ui` and `location /v3/api-docs` proxy blocks to
  `frontend/nginx.conf` (same upstream + headers as `/api/`).
- Out of scope: auth on the docs (backend already gates via profile + security allowlist); changing
  the backend.

## Technical approach
- Two extra `location` blocks proxying to `http://backend:8080`, mirroring the existing `/api/`
  block. Swagger UI's own asset + `swagger-config` requests are all under `/swagger-ui` and
  `/v3/api-docs`, so both prefixes are covered and the UI renders when opened via the app origin.

## Acceptance criteria
- [x] AC-1 — `http://localhost:8081/v3/api-docs` returns the spec (dev).
- [x] AC-2 — `http://localhost:8081/swagger-ui/index.html` renders Swagger UI (dev).
- [x] AC-3 — `/api` proxying and SPA fallback are unchanged.
- [x] AC-4 — In prod (docs disabled) the proxied paths return the backend's 404 — no docs exposed.

## Test plan
- **Positive (dev):** curl `:8081/v3/api-docs` → 200; open `:8081/swagger-ui/index.html` → UI loads
  and lists the operations (its JS fetches `/v3/api-docs` same-origin through the proxy).
- **Negative (prod):** with `SPRING_PROFILES_ACTIVE=prod` the backend disables docs, so the proxied
  paths return 404 — verified by the backend behavior (application-prod.yml), not re-tested here.
- Regression: existing E2E suite still green (SPA + `/api` unaffected).

## Definition of Done
- [x] `frontend/nginx.conf` proxies `/swagger-ui` and `/v3/api-docs`
- [x] Frontend image rebuilt + verified live on `:8081`
- [x] README updated (docs URLs now on the app origin); INDEX.md updated

## Implementation notes (as built)
- `frontend/nginx.conf`: added `location /swagger-ui` and `location /v3/api-docs` proxying to
  `http://backend:8080` with the same forwarded headers as `/api/`.
- Rebuilt `hts-frontend:dev`, recreated the container; verified `:8081/v3/api-docs` (200) and
  `:8081/swagger-ui/index.html` (UI renders). README's API-docs table now shows the `:8081` URLs
  with `:8080` as the direct alternative.
