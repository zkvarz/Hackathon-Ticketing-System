# EP-10 — Improvements & Tech Debt (optional, non-blocking)

**Goal.** Performance and internal-quality improvements surfaced *during build* (not from the
spec). None of these gate delivery — the mandatory scope (EP-01..EP-08) stands without them.
They are recorded so the findings aren't lost and can be picked up if time allows or if the
project scales past the hackathon bar.

**Same standards apply:** BE tickets carry unit (positive/negative/boundary) + Testcontainers
integration tests. Any change touching the data model or API updates
`requirements-analysis.md` / `architecture.md` first (spec-driven protocol, AGENTS.md §6).

## Stories & tickets

| Story | Ticket | Type | Title |
|-------|--------|------|-------|
| ST-01 Board scale | HTS-044 | BE (perf) | Trigram (`pg_trgm` GIN) index for title substring search |
| ST-02 Timestamp integrity | HTS-045 | BE (tech-debt) | Single-source `modified_at` (clock-driven, drop the dual write) |

**Origin.** Both were raised in the HTS-019/025/029 review. HTS-044 addresses the `LIKE '%…%'`
search added in HTS-029 (can't use a b-tree index); HTS-045 addresses the `modified_at`
dual-source (`BaseEntity.@PreUpdate` now() vs. service clock) noted in HTS-019.
