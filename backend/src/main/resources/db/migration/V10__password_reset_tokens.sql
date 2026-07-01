-- Password reset tokens (HTS-037), architecture.md §6/§10. Single-use, short TTL (1h default).
-- Mirrors email_verification_tokens (V3): a separate table so the two flows never share a token.
-- FK cascades on user delete so tokens never outlive their user.
CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token       VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL,
    modified_at TIMESTAMPTZ NOT NULL
);

-- Tokens are looked up by value and must be globally unique.
CREATE UNIQUE INDEX ux_prt_token ON password_reset_tokens (token);
-- Supports invalidating a user's prior unused reset tokens on reissue.
CREATE INDEX ix_prt_user ON password_reset_tokens (user_id);
