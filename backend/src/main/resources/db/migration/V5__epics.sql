-- Epics (HTS-017), architecture.md §6. team_id is immutable (FR-E2); no cascade on team delete
-- (FR-T5/E8 are enforced as 409 in the service), so the FK also backstops referenced-deletes.
CREATE TABLE epics (
    id          UUID PRIMARY KEY,
    team_id     UUID NOT NULL REFERENCES teams (id),
    title       VARCHAR(200) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL,
    modified_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_epics_team ON epics (team_id);
