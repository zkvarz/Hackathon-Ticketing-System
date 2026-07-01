# HTS-033 — [BE] Security hardening: env secrets, headers, no committed secrets, CSRF/SameSite review

| Field | Value |
|-------|-------|
| **ID** | HTS-033 |
| **Type** | BE |
| **Epic** | EP-08 Cross-cutting & Delivery |
| **Story** | ST-02 Security hardening |
| **Status** | TODO |
| **Depends on** | HTS-013 |
| **Blocks** | HTS-035 |
| **Traceability** | NFR-1; DoD-8; FR-P6; architecture.md §5, §9 |

## Goal
Harden the backend: all secrets from environment, sensible security headers, verified CSRF and
cookie flags, and a guarantee that no secret is committed.

## Scope
- In scope: confirm all credentials/SMTP config read from env (no defaults with real secrets);
  security response headers (e.g. content-type-options, frame-options, HSTS where applicable);
  review `SameSite`/`Secure`/`HttpOnly` cookie flags; ensure tokens never appear in URLs except
  the verification token (FR-P6); a repo scan/test asserting no secret literals.
- Out of scope: feature auth logic (EP-02).

## Technical approach
- Externalize all config (architecture.md §5); fail fast if a required secret is missing.
- Add security headers via Spring Security config.
- Add a build/test check (or documented `git grep`/secret-scan) for committed secrets (DoD-8).

## Acceptance criteria
- [x] AC-1 — No real secret/credential is present in any committed file or default.
- [x] AC-2 — Security headers are present on responses.
- [x] AC-3 — Cookie flags are `HttpOnly`+`SameSite`(+`Secure` in non-local profiles).
- [x] AC-4 — Session/bearer tokens never appear in URLs (only the verification token may).

## Test plan
**Unit / Spring test:**
- Positive: responses include expected security headers; cookie flags set.
- Negative: missing required env var → app fails to start with a clear message.
- Boundary: verification URL contains a token (allowed); no other endpoint emits a token in a URL/redirect.

**Integration (Testcontainers — Postgres):**
- Boot with a complete env → secured responses; boot with a missing secret → startup failure (asserted).

## How to run / verify
```bash
cd backend && ./mvnw test -Dtest='*Security*'
git grep -nE '(password|secret|token)\s*=\s*["'"'"']' -- ':!*test*'   # expect no real secrets
```

## Definition of Done
- [x] AC-1..AC-4 met
- [x] Tests pass (positive/negative/boundary) — `RequiredSecretsValidatorSecurityTest` (3),
  `SecurityHeadersIntegrationTest` (2); existing `EndpointSecurityIntegrationTest` still green
- [x] Secret scan clean (DoD-8) — only match is a UI validation string, not a secret
- [x] INDEX.md status updated

## Implementation notes (as built)
- Headers via Spring Security: `X-Content-Type-Options: nosniff` + `X-Frame-Options: DENY`
  (defaults) plus an explicit `Referrer-Policy: strict-origin-when-cross-origin`; HSTS configured
  (emitted over HTTPS only, so it activates in the prod profile).
- `application-prod.yml`: no secret defaults (every value is a bare `${ENV}` → missing = startup
  failure) and `server.servlet.session.cookie.secure: true`. Default/local profile keeps
  `HttpOnly` + `SameSite=Lax`.
- `RequiredSecretsValidator` (`@Profile("prod")`) aborts startup with one clear, aggregated
  message listing any missing/blank required secret, before the opaque downstream failure.
- Added `.gitattributes` (`* text=auto eol=lf`, `mvnw text eol=lf`, binary rules) per the
  carry-over note; `backend/mvnw` retains mode `100755`.

## Carry-over notes (repo hygiene, from EP-01/EP-02 implementation)
- **Add a `.gitattributes`** (`* text=auto eol=lf` + binary rules). The repo is developed on
  Windows: every commit emits LF→CRLF warnings, and `backend/mvnw` had to be re-marked
  executable (mode 100755) after the first CI run failed with `Permission denied` (exit 126).
  A `.gitattributes` normalizes EOLs and, with `backend/mvnw text eol=lf`, prevents the shell
  wrapper from being checked out with CRLF. (Small, isolated change — could also be its own
  INFRA ticket if preferred.)
- **Verify the Maven wrapper keeps its exec bit** as part of the secret/hygiene scan.
