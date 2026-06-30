-- Baseline migration (HTS-002).
-- No application tables yet — those are created by feature migrations in later tickets.
-- A fresh database therefore contains only the Flyway history table after migration (DoD-9).
-- Enable pgcrypto so gen_random_uuid()/digest() are available to future migrations if needed.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
