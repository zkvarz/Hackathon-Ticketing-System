# EP-08 — Cross-cutting & Delivery

**Goal.** The non-functional and delivery glue: a standardized API error model, consistent
frontend UX states, security hardening, the README, a fresh-DB/no-seed verification, the DoD
acceptance run-through, and a small should-have E2E suite.

**Why.** These close out NFR-1/3/5 and the Definition of Done. Several can start early (the
error model and UX states are dependencies of other epics) and finish late (DoD run-through is
last).

**Architecture references:** §5 (config/secrets), §8 (error model), §12 (testing), §13 (run).

## Stories & tickets

| Story | Ticket | Type | Title |
|-------|--------|------|-------|
| ST-01 Error model & UX states | HTS-031 | BE | Standardized error response model + global exception handling |
| ST-01 Error model & UX states | HTS-032 | FE | Global loading/empty/error/success UX + error boundary |
| ST-02 Security hardening | HTS-033 | BE | Env secrets, headers, no committed secrets, CSRF/SameSite review |
| ST-03 Docs | HTS-034 | DOCS | README: prerequisites, configuration, startup, run/test commands |
| ST-04 Delivery acceptance | HTS-035 | QA | Fresh-DB/no-seed verification + DoD acceptance checklist |
| ST-05 E2E (should-have) | HTS-036 | QA/E2E | Playwright critical-path suite |

**Exit criteria:** all errors return the standard model with correct codes; the FE shows
appropriate states everywhere; no secret is committed; README lets a fresh dev run everything;
a fresh DB is empty; DoD-1..10 pass; the E2E suite is green.
