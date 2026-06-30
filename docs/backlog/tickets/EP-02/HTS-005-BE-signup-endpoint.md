# HTS-005 — [BE] Sign-up endpoint: validation, Argon2id hash, unique-CI email, persist unverified

| Field | Value |
|-------|-------|
| **ID** | HTS-005 |
| **Type** | BE |
| **Epic** | EP-02 Authentication |
| **Story** | ST-01 Sign up |
| **Status** | TODO |
| **Depends on** | HTS-002 |
| **Blocks** | HTS-006, HTS-007 |
| **Traceability** | FR-A1, FR-A2, FR-A4, FR-A5; AMB-1, AMB-2; architecture.md §6, §9 |

## Goal
Accept a new account registration, validate it, store the user with an Argon2id password hash
and a case-insensitively-unique email, and create the account in an unverified state.

## Scope
- In scope: `User` entity + repository + migration; `POST /api/auth/signup`; request
  validation; Argon2id hashing; CI-unique email enforcement; persist `email_verified=false`.
- Out of scope: sending the verification email + token (HTS-007); login (HTS-011).

## Technical approach
- `User` entity per architecture.md §6 (UUIDv7 id, email, password_hash, email_verified,
  timestamps). Flyway migration adds the table + unique index on `lower(email)`.
- DTO validation: email format; password length 8..128 (AMB-1).
- Email normalized to `lower(trim(email))` for storage/comparison (FR-A2 / AMB-9 pattern).
- `Argon2PasswordEncoder` (Spring Security crypto) for hashing (AMB-2); never store plaintext.
- Duplicate email → 409 with standard error model (`code=EMAIL_TAKEN`).

## Acceptance criteria
- [ ] AC-1 — Valid signup persists an unverified user; response excludes the password hash.
- [ ] AC-2 — Password is stored only as an Argon2id hash (never plaintext).
- [ ] AC-3 — Duplicate email (case-insensitive, whitespace-trimmed) returns 409 `EMAIL_TAKEN`.
- [ ] AC-4 — Invalid email or password <8 / >128 chars returns 400 with field errors.

## Test plan
**Unit (JUnit 5 + Mockito):**
- Positive: valid request → service hashes password, saves user with `email_verified=false`.
- Negative: duplicate email (mock repo finds existing) → conflict; malformed email → validation error.
- Boundary: password length 7 (reject), 8 (accept), 128 (accept), 129 (reject); email with surrounding spaces + mixed case is normalized and collides with an existing lower-cased one.

**Integration (Testcontainers — Postgres):**
- `POST /api/auth/signup` persists a row; the unique index rejects a second signup differing only by case/whitespace (returns 409).
- Stored `password_hash` starts with the Argon2id identifier and does not equal the raw password.

## How to run / verify
```bash
cd backend && ./mvnw test -Dtest='*SignUp*'
docker compose up --build backend db
curl -X POST localhost:8080/api/auth/signup -H 'Content-Type: application/json' \
  -d '{"email":"a@b.com","password":"password1"}'
```

## Definition of Done
- [ ] AC-1..AC-4 met
- [ ] Unit tests (positive/negative/boundary) pass
- [ ] Testcontainers integration test passes (unique-CI + hash assertions)
- [ ] No plaintext password persisted or logged
- [ ] INDEX.md status updated
