-- Users table (HTS-005). UUIDv7 id + UTC timestamps come from BaseEntity (architecture.md §6).
-- Email is stored already-normalized (lower(trim(...))); the functional unique index enforces
-- case-insensitive uniqueness (FR-A2 / AMB-9). Password is only ever an Argon2id hash (FR-A5).
CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL,
    modified_at   TIMESTAMPTZ NOT NULL
);

-- Case-insensitive uniqueness. Email is normalized before insert, so lower(email) == email,
-- but indexing on lower(email) makes the guarantee explicit and robust.
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));
