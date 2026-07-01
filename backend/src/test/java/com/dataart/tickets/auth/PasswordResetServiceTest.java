package com.dataart.tickets.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for password reset (HTS-037): request-side token rotation + no-enumeration, and
 * reset-side positive/negative/boundary (valid reset, unknown/consumed/expired token, token exactly
 * at expiry). New-password length bounds live on the DTO and are covered by
 * {@link com.dataart.tickets.auth.ResetPasswordValidationTest}.
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-30T10:00:00Z");
    private static final Duration TTL = Duration.ofHours(1);

    @Mock
    private PasswordResetTokenRepository tokens;
    @Mock
    private UserRepository users;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private PasswordEncoder passwordEncoder;

    private PasswordResetService service() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new PasswordResetService(
                tokens, users, mailSender, passwordEncoder, clock, TTL,
                "http://localhost:8081", "no-reply@tickets.local");
    }

    private PasswordResetToken unconsumed(User user, Instant expiresAt) {
        return new PasswordResetToken(user, "old", expiresAt);
    }

    // AC-1 (positive + boundary): existing user with prior unused tokens → all invalidated, one new
    // issued + emailed.
    @Test
    void requestResetInvalidatesPriorTokensAndIssuesNew() {
        User user = new User("a@b.com", "$argon2$h");
        PasswordResetToken t1 = unconsumed(user, NOW.plus(Duration.ofMinutes(5)));
        when(users.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(tokens.findByUserAndConsumedAtIsNull(user)).thenReturn(List.of(t1));
        when(tokens.save(any(PasswordResetToken.class))).thenAnswer(i -> i.getArgument(0));

        service().requestReset("  A@B.com ");

        assertThat(t1.getConsumedAt()).isEqualTo(NOW);
        verify(tokens).save(any(PasswordResetToken.class)); // exactly one new token
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    // Boundary: existing user with zero prior tokens still issues exactly one.
    @Test
    void requestResetWithNoPriorTokensStillIssuesOne() {
        User user = new User("a@b.com", "$argon2$h");
        when(users.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(tokens.findByUserAndConsumedAtIsNull(user)).thenReturn(List.of());
        when(tokens.save(any(PasswordResetToken.class))).thenAnswer(i -> i.getArgument(0));

        service().requestReset("a@b.com");

        verify(tokens).save(any(PasswordResetToken.class));
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    // AC-4 (negative): unknown email → nothing happens (no enumeration signal).
    @Test
    void requestResetForUnknownEmailDoesNothing() {
        when(users.findByEmail("ghost@b.com")).thenReturn(Optional.empty());

        service().requestReset("ghost@b.com");

        verify(tokens, never()).save(any());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    // AC-2 (positive): a valid token sets a new hash and consumes the token.
    @Test
    void resetWithValidTokenSetsNewHashAndConsumes() {
        User user = new User("a@b.com", "$argon2$OLD");
        PasswordResetToken token = unconsumed(user, NOW.plus(Duration.ofMinutes(30)));
        when(tokens.findByToken("tok")).thenReturn(Optional.of(token));
        when(tokens.findByUserAndConsumedAtIsNull(user)).thenReturn(List.of(token));
        when(passwordEncoder.encode("new-password")).thenReturn("$argon2$NEW");

        service().reset("tok", "new-password");

        assertThat(user.getPasswordHash()).isEqualTo("$argon2$NEW");
        assertThat(token.getConsumedAt()).isEqualTo(NOW);
    }

    // AC-3 (negative): unknown token → TokenInvalidException, no hash change.
    @Test
    void resetWithUnknownTokenThrows() {
        when(tokens.findByToken("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().reset("ghost", "new-password"))
                .isInstanceOf(TokenInvalidException.class);
    }

    // AC-3 (negative): already-consumed token → rejected.
    @Test
    void resetWithConsumedTokenThrows() {
        User user = new User("a@b.com", "$argon2$OLD");
        PasswordResetToken token = unconsumed(user, NOW.plus(Duration.ofMinutes(30)));
        token.setConsumedAt(NOW.minus(Duration.ofMinutes(1)));
        when(tokens.findByToken("tok")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service().reset("tok", "new-password"))
                .isInstanceOf(TokenInvalidException.class);
        assertThat(user.getPasswordHash()).isEqualTo("$argon2$OLD");
    }

    // AC-3 boundary: token exactly at expiry is rejected (expiry is exclusive).
    @Test
    void resetWithTokenExactlyAtExpiryThrows() {
        User user = new User("a@b.com", "$argon2$OLD");
        PasswordResetToken token = unconsumed(user, NOW); // expiresAt == now
        when(tokens.findByToken("tok")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service().reset("tok", "new-password"))
                .isInstanceOf(TokenInvalidException.class);
        assertThat(user.getPasswordHash()).isEqualTo("$argon2$OLD");
    }
}
