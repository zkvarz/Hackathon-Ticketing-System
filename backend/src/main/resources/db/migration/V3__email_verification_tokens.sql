-- Email verification tokens (HTS-007), architecture.md §6/§10. Single-use, 24h TTL.
-- FK cascades on user delete so tokens never outlive their user.
CREATE TABLE email_verification_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token       VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL,
    modified_at TIMESTAMPTZ NOT NULL
);

-- Tokens are looked up by value and must be globally unique.
CREATE UNIQUE INDEX ux_evt_token ON email_verification_tokens (token);
-- Supports invalidating a user's prior unused tokens on resend (FR-A11, HTS-009).
CREATE INDEX ix_evt_user ON email_verification_tokens (user_id);
