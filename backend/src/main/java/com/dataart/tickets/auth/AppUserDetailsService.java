package com.dataart.tickets.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Loads users for Spring Security authentication (HTS-011). The principal is the normalized
 * email; the stored Argon2id hash is the credential. Unverified accounts are marked
 * <em>disabled</em>, so authentication fails with {@code DisabledException} → mapped to 403
 * {@code EMAIL_NOT_VERIFIED} (FR-A7), distinct from bad credentials (401).
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        String email = EmailNormalizer.normalize(username);
        User user = users.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No user for " + email));

        return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .disabled(!user.isEmailVerified())
                .authorities(List.of())
                .build();
    }
}
