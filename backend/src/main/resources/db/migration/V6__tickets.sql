-- Tickets (HTS-019), architecture.md §6. type/state are stored as the enum constant name and
-- CHECK-constrained here as a second line of defence behind server-side enum validation (FR-K8).
-- No cascade on team_id/epic_id: those deletes are blocked as 409 while referenced (FR-T5/E8).
-- created_by references the authenticated user (FR-K1). Comment cascade is defined in V7.
CREATE TABLE tickets (
    id          UUID PRIMARY KEY,
    team_id     UUID NOT NULL REFERENCES teams (id),
    epic_id     UUID REFERENCES epics (id),
    type        VARCHAR(20) NOT NULL,
    state       VARCHAR(30) NOT NULL,
    title       VARCHAR(200) NOT NULL,
    body        TEXT NOT NULL,
    created_by  UUID NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ NOT NULL,
    modified_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_tickets_type CHECK (type IN ('BUG', 'FEATURE', 'FIX')),
    CONSTRAINT ck_tickets_state CHECK (state IN (
        'NEW', 'READY_FOR_IMPLEMENTATION', 'IN_PROGRESS', 'READY_FOR_ACCEPTANCE', 'DONE'))
);

-- Board queries filter by team and order by modified-at desc (FR-B1/B7); epic index backs the
-- referenced-delete check (FR-E8); created_by index backs the FK.
CREATE INDEX ix_tickets_team ON tickets (team_id);
CREATE INDEX ix_tickets_epic ON tickets (epic_id);
CREATE INDEX ix_tickets_created_by ON tickets (created_by);
