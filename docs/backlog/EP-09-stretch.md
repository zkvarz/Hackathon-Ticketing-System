# EP-09 — Stretch (optional, non-blocking)

**Goal.** Optional enhancements from the spec's stretch list (§2.10 of the analysis). None of
these gate delivery; implement only after the mandatory scope (EP-01..EP-08) is green.

**Same standards apply:** BE tickets carry unit (positive/negative/boundary) + Testcontainers
integration tests; FE tickets carry component + MSW tests.

## Stories & tickets

| Story | Ticket | Type | Title |
|-------|--------|------|-------|
| ST-01 Password reset | HTS-037 | BE | Password reset flow (backend) |
| ST-01 Password reset | HTS-038 | FE | Password reset flow (frontend) |
| ST-02 Comment edit/delete | HTS-039 | BE | Edit/delete own comments (backend) |
| ST-02 Comment edit/delete | HTS-040 | FE | Edit/delete own comments (frontend) |
| ST-03 Activity history | HTS-041 | BE | Ticket activity history (backend) |
| ST-03 Activity history | HTS-042 | FE | Ticket activity history (frontend) |
| ST-04 Virtualization | HTS-043 | FE | Virtualized board rendering for large boards |

**Note:** these change the mandatory data model only additively (e.g. a history table,
mutable comments). If implemented, update `requirements-analysis.md` and `architecture.md`
accordingly, and confirm comment immutability assumptions (FR-C6) are intentionally relaxed.
