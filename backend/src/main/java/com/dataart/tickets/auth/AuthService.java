package com.dataart.tickets.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication/account business logic. HTS-005 covers sign-up: normalize the email, enforce
 * case-insensitive uniqueness (FR-A2), Argon2id-hash the password (FR-A5), and persist the user
 * unverified (FR-A7). Verification-email issuance is wired in HTS-007; login in HTS-011.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Register a new, unverified account.
     *
     * @throws EmailAlreadyTakenException if the normalized email already exists
     */
    @Transactional
    public User signup(String rawEmail, String rawPassword) {
        String email = EmailNormalizer.normalize(rawEmail);

        // Pre-check for a friendly 409; the unique index is the actual race-safe guard.
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyTakenException(email);
        }

        String hash = passwordEncoder.encode(rawPassword);
        try {
            return users.saveAndFlush(new User(email, hash));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Lost the race against a concurrent signup with the same email.
            throw new EmailAlreadyTakenException(email);
        }
    }
}
