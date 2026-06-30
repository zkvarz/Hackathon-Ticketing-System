package com.dataart.tickets.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for resend verification (HTS-009) — token rotation + no-enumeration behavior.
 */
@ExtendWith(MockitoExtension.class)
class ResendServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-30T10:00:00Z");
    private static final Duration TTL = Duration.ofHours(24);

    @Mock
    private EmailVerificationTokenRepository tokens;
    @Mock
    private UserRepository users;
    @Mock
    private JavaMailSender mailSender;

    private EmailVerificationService service() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new EmailVerificationService(
                tokens, users, mailSender, clock, TTL,
                "http://localhost:8081", "no-reply@tickets.local");
    }

    private EmailVerificationToken unconsumed(User user) {
        return new EmailVerificationToken(user, "old", NOW.plus(Duration.ofHours(5)));
    }

    // Positive + boundary: unverified user with multiple unused tokens → all invalidated, one new issued + sent.
    @Test
    void resendInvalidatesPriorTokensAndIssuesNew() {
        User user = new User("a@b.com", "$argon2$h"); // unverified by default
        EmailVerificationToken t1 = unconsumed(user);
        EmailVerificationToken t2 = unconsumed(user);
        when(users.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(tokens.findByUserAndConsumedAtIsNull(user)).thenReturn(List.of(t1, t2));
        when(tokens.save(any(EmailVerificationToken.class))).thenAnswer(i -> i.getArgument(0));

        service().resend("  A@B.com ");

        assertThat(t1.getConsumedAt()).isEqualTo(NOW);
        assertThat(t2.getConsumedAt()).isEqualTo(NOW);
        verify(tokens).save(any(EmailVerificationToken.class)); // exactly one new token
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    // Boundary: unverified user with zero prior tokens still issues exactly one.
    @Test
    void resendWithNoPriorTokensStillIssuesOne() {
        User user = new User("a@b.com", "$argon2$h");
        when(users.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(tokens.findByUserAndConsumedAtIsNull(user)).thenReturn(List.of());
        when(tokens.save(any(EmailVerificationToken.class))).thenAnswer(i -> i.getArgument(0));

        service().resend("a@b.com");

        verify(tokens).save(any(EmailVerificationToken.class));
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    // Negative: already-verified user → nothing issued or sent (but no error — generic success).
    @Test
    void resendForVerifiedUserDoesNothing() {
        User user = new User("a@b.com", "$argon2$h");
        user.setEmailVerified(true);
        when(users.findByEmail("a@b.com")).thenReturn(Optional.of(user));

        service().resend("a@b.com");

        verify(tokens, never()).save(any());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    // Negative: unknown email → nothing happens (no enumeration signal).
    @Test
    void resendForUnknownEmailDoesNothing() {
        when(users.findByEmail("ghost@b.com")).thenReturn(Optional.empty());

        service().resend("ghost@b.com");

        verify(tokens, never()).save(any());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }
}
