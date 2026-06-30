package com.dataart.tickets.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Lookup by already-normalized (lower/trim) email. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
