-- Teams (HTS-015), architecture.md §6. Name stored trimmed; unique case-insensitively.
CREATE TABLE teams (
    id          UUID PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    modified_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ux_teams_name_lower ON teams (lower(name));
