-- Trigram GIN index for the board's title substring search (HTS-044; FR-B9/B10, AMB-4/AMB-10).
-- HTS-029 implements ?q= as `lower(title) LIKE lower('%…%')`. A leading-wildcard LIKE cannot use
-- a b-tree index, so it degrades to a sequential scan that grows linearly with the board. The
-- pg_trgm extension provides a GIN index that accelerates substring/ILIKE matching, letting the
-- planner prune with a bitmap index scan past the 100+ ticket bar (FR-B10). The index expression
-- (lower(title)) matches the query predicate exactly so the planner can use it.
--
-- pg_trgm ships with the postgres:16 image (contrib). Both statements are idempotent so the
-- migration is safe to re-run against a partially-migrated database.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS ix_tickets_title_trgm ON tickets USING gin (lower(title) gin_trgm_ops);
