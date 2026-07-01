-- Board query index (HTS-025). The board lists a team's tickets ordered most-recently-modified
-- first (FR-B7) and must stay fast at the 100+ ticket bar (FR-B10 / AMB-4). A composite
-- (team_id, modified_at DESC) index serves both the ordered board query and plain team_id lookups
-- (leftmost prefix), so the standalone team index from V6 becomes redundant and is dropped.
DROP INDEX ix_tickets_team;
CREATE INDEX ix_tickets_team_modified ON tickets (team_id, modified_at DESC);
