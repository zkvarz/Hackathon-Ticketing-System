package com.dataart.tickets.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the Spring Security user lookup (HTS-011).
 */
@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

    @Mock
    private UserRepository users;

    @InjectMocks
    private AppUserDetailsService service;

    private User user(boolean verified) {
        User u = new User("a@b.com", "$argon2id$hash");
        u.setEmailVerified(verified);
        return u;
    }

    // Positive: a verified user loads as an enabled UserDetails carrying the stored hash.
    @Test
    void loadsVerifiedUserAsEnabled() {
        when(users.findByEmail("a@b.com")).thenReturn(Optional.of(user(true)));

        UserDetails details = service.loadUserByUsername("a@b.com");

        assertThat(details.getUsername()).isEqualTo("a@b.com");
        assertThat(details.getPassword()).isEqualTo("$argon2id$hash");
        assertThat(details.isEnabled()).isTrue();
    }

    // Boundary: an unverified user is disabled → DisabledException at authentication time.
    @Test
    void loadsUnverifiedUserAsDisabled() {
        when(users.findByEmail("a@b.com")).thenReturn(Optional.of(user(false)));

        assertThat(service.loadUserByUsername("a@b.com").isEnabled()).isFalse();
    }

    // Boundary: a mixed-case/whitespace username is normalized before lookup.
    @Test
    void normalizesUsernameBeforeLookup() {
        when(users.findByEmail("a@b.com")).thenReturn(Optional.of(user(true)));

        assertThat(service.loadUserByUsername("  A@B.com ").getUsername()).isEqualTo("a@b.com");
    }

    // Negative: an unknown email throws UsernameNotFoundException (→ generic bad credentials).
    @Test
    void unknownUserThrows() {
        when(users.findByEmail("ghost@b.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost@b.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
