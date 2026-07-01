-- Comments table (schema per architecture.md §6). The comment entity + API arrive in HTS-023;
-- the table and its cascade FK are established here because "deleting a ticket also deletes its
-- comments" is a ticket concern (FR-K6), exercised by HTS-019's cascade integration test.
-- ON DELETE CASCADE on ticket_id is intentional (contrast the referenced-delete blocks on
-- team/epic). author_id has no cascade — users are not deleted in scope.
CREATE TABLE comments (
    id         UUID PRIMARY KEY,
    ticket_id  UUID NOT NULL REFERENCES tickets (id) ON DELETE CASCADE,
    author_id  UUID NOT NULL REFERENCES users (id),
    body       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_comments_ticket ON comments (ticket_id);
