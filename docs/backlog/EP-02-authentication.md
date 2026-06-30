# EP-02 — Authentication & Accounts

**Goal.** Local email/password authentication with SMTP email verification (via Mailpit in
dev), login/logout on server-side session cookies, and enforcement that gates the rest of the
application behind a verified, authenticated user.

**Why.** FR-A12 requires that all business screens/endpoints (except the auth set) require
authentication, and FR-A7 blocks unverified accounts — so this epic gates EP-03…EP-07. It
also delivers DoD-1, the headline acceptance gate.

**Architecture references:** §5 (config: token TTL, SMTP, session timeouts), §9 (auth/session
flow), §10 (email verification flow), §6 (User + EmailVerificationToken entities).

## Stories & tickets

| Story | Ticket | Type | Title |
|-------|--------|------|-------|
| ST-01 Sign up | HTS-005 | BE | Sign-up endpoint (validation, Argon2id, unique-CI email) |
| ST-01 Sign up | HTS-006 | FE | Sign-up screen |
| ST-02 Email verification | HTS-007 | BE | Verification token issue + send + verify endpoint |
| ST-02 Email verification | HTS-008 | FE | Email verification result screen |
| ST-03 Resend | HTS-009 | BE | Resend verification (invalidate prior tokens) |
| ST-03 Resend | HTS-010 | FE | Resend verification action |
| ST-04 Login/logout | HTS-011 | BE | Login/logout (session cookie, reject unverified) |
| ST-04 Login/logout | HTS-012 | FE | Login screen + logout control |
| ST-05 Enforcement | HTS-013 | BE | Secure endpoints + CSRF + 401/403 model |
| ST-05 Enforcement | HTS-014 | FE | Route guards + auth context + 401 handling |

**Exit criteria:** a user can sign up, receive a verification email in Mailpit, verify, log in,
and reach guarded screens; unverified users are blocked; unauthenticated API calls get 401;
logout invalidates the session (DoD-1).
