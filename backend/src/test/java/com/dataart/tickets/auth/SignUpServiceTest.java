package com.dataart.tickets.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for sign-up service logic (HTS-005), collaborators mocked.
 * Positive / negative / boundary per architecture.md §12.
 */
@ExtendWith(MockitoExtension.class)
class SignUpServiceTest {

    @Mock
    private UserRepository users;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    // Positive: a valid request hashes the password and saves an unverified user.
    @Test
    void signupHashesPasswordAndSavesUnverifiedUser() {
        when(users.existsByEmail("a@b.com")).thenReturn(false);
        when(passwordEncoder.encode("password1")).thenReturn("$argon2id$hashed");
        when(users.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = authService.signup("a@b.com", "password1");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("a@b.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("$argon2id$hashed");
        assertThat(saved.getValue().isEmailVerified()).isFalse();
        assertThat(result.getPasswordHash()).isNotEqualTo("password1");
    }

    // Boundary/behavior: email is normalized (trimmed + lower-cased) before lookup and storage.
    @Test
    void signupNormalizesEmailBeforeCheckAndSave() {
        when(users.existsByEmail("foo@bar.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$argon2id$h");
        when(users.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.signup("  Foo@BAR.com  ", "password1");

        verify(users).existsByEmail("foo@bar.com");
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("foo@bar.com");
    }

    // Negative: a duplicate email is rejected and nothing is persisted.
    @Test
    void signupRejectsDuplicateEmail() {
        when(users.existsByEmail("a@b.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup("A@B.com", "password1"))
                .isInstanceOf(EmailAlreadyTakenException.class);

        verify(users, never()).saveAndFlush(any());
        verify(passwordEncoder, never()).encode(anyString());
    }
}
