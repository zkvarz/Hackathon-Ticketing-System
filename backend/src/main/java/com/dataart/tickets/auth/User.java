package com.dataart.tickets.auth;

import com.dataart.tickets.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Application user (architecture.md §6). Email is stored normalized (lower(trim)) so the
 * unique index enforces case-insensitive uniqueness (FR-A2 / AMB-9). The password is only
 * ever persisted as an Argon2id hash (FR-A5); plaintext never touches the entity.
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    // Uniqueness is enforced case-insensitively by a functional unique index on lower(email)
    // in the migration (FR-A2 / AMB-9); the value here is always stored already-normalized.
    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    protected User() {
        // JPA
    }

    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.emailVerified = false;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    /** Replace the stored hash (password reset, HTS-037). Callers pass an already-Argon2id hash. */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }
}
