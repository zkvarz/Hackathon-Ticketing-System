# HTS-005 — [BE] Sign-up endpoint: validation, Argon2id hash, unique-CI email, persist unverified

| Field | Value |
|-------|-------|
| **ID** | HTS-005 |
| **Type** | BE |
| **Epic** | EP-02 Authentication |
| **Story** | ST-01 Sign up |
| **Status** | DONE |
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
- [x] AC-1 — Valid signup persists an unverified user; response excludes the password hash.
- [x] AC-2 — Password is stored only as an Argon2id hash (never plaintext).
- [x] AC-3 — Duplicate email (case-insensitive, whitespace-trimmed) returns 409 `EMAIL_TAKEN`.
- [x] AC-4 — Invalid email or password <8 / >128 chars returns 400 with field errors.

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
- [x] AC-1..AC-4 met
- [x] Unit tests (positive/negative/boundary) pass
- [x] Testcontainers integration test passes (unique-CI + hash assertions)
- [x] No plaintext password persisted or logged
- [x] INDEX.md status updated

## Implementation notes
- `auth/`: `User` entity (extends `BaseEntity`), `UserRepository`, `AuthService`,
  `AuthController` (`POST /api/auth/signup` → 201 `UserResponse`, no hash), `SignupRequest`
  (Bean Validation: email format + password 8..128; compact constructor trims the email so
  whitespace-padded inputs validate then normalize), `EmailNormalizer` (lower+trim, root
  locale), `EmailAlreadyTakenException`.
- Migration `V2__users.sql`: `users` table + functional unique index on `lower(email)`
  (case-insensitive uniqueness, FR-A2/AMB-9).
- Argon2id via `Argon2PasswordEncoder` (`config/PasswordEncoderConfig`); added
  `spring-security-crypto` + `bcprov-jdk18on` (pinned 1.79) to the pom.
- `common/ApiExceptionHandler` (focused): 400 `VALIDATION_FAILED` with field errors, 409
  `EMAIL_TAKEN`. HTS-031 will generalize this into the full global handler.
- Duplicate detection is pre-checked for a friendly 409 and also guarded by catching
  `DataIntegrityViolationException` (race-safe via the unique index).
- Tests (12, all green via Testcontainers/podman): `SignUpServiceTest` (Mockito: hash+save
  unverified, normalization, duplicate rejected without save), `SignUpValidationTest`
  (password 7/8/128/129 boundaries, email format), `SignUpIntegrationTest` (Postgres:
  persists unverified + Argon2 hash, CI-duplicate → 409, short password → 400).
- Also corrected `BaselineIntegrationTest`: DoD-9 means no preloaded *data*, not zero tables
  (the schema legitimately grows) — now asserts `users` is empty on a fresh DB.
