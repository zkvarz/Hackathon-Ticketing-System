package com.dataart.tickets.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for email verification (HTS-007) with a fixed clock for deterministic expiry.
 */
@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-30T10:00:00Z");
    private static final Duration TTL = Duration.ofHours(24);

    @Mock
    private EmailVerificationTokenRepository tokens;

    @Mock
    private JavaMailSender mailSender;

    private EmailVerificationService serviceAt(Instant clockInstant) {
        Clock clock = Clock.fixed(clockInstant, ZoneOffset.UTC);
        return new EmailVerificationService(
                tokens, mailSender, clock, TTL, "http://localhost:8081", "no-reply@tickets.local");
    }

    private EmailVerificationToken tokenExpiringAt(Instant expiresAt) {
        return new EmailVerificationToken(new User("a@b.com", "$argon2$h"), "tok", expiresAt);
    }

    // Positive: issuing saves a token expiring at now+TTL and emails the link.
    @Test
    void issueAndSendSavesTokenAndEmailsLink() {
        when(tokens.save(any(EmailVerificationToken.class))).thenAnswer(i -> i.getArgument(0));
        User user = new User("a@b.com", "$argon2$h");

        serviceAt(NOW).issueAndSend(user);

        ArgumentCaptor<EmailVerificationToken> saved =
                ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokens).save(saved.capture());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(TTL));

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        assertThat(mail.getValue().getText())
                .contains("/verify?token=" + saved.getValue().getToken());
        assertThat(mail.getValue().getTo()).containsExactly("a@b.com");
    }

    // Best-effort send: a mail failure does not propagate; the token is still issued.
    @Test
    void issueAndSendSwallowsMailFailure() {
        when(tokens.save(any(EmailVerificationToken.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        serviceAt(NOW).issueAndSend(new User("a@b.com", "$argon2$h"));

        verify(tokens).save(any(EmailVerificationToken.class));
    }

    // Positive: a valid token marks the user verified and consumes the token.
    @Test
    void verifyMarksUserVerifiedAndConsumesToken() {
        EmailVerificationToken token = tokenExpiringAt(NOW.plus(Duration.ofHours(1)));
        when(tokens.findByToken("tok")).thenReturn(Optional.of(token));

        User result = serviceAt(NOW).verify("tok");

        assertThat(result.isEmailVerified()).isTrue();
        assertThat(token.getConsumedAt()).isEqualTo(NOW);
    }

    // Negative: an unknown token is invalid.
    @Test
    void verifyUnknownTokenThrows() {
        when(tokens.findByToken("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceAt(NOW).verify("nope"))
                .isInstanceOf(TokenInvalidException.class);
    }

    // Negative: an already-consumed token cannot be reused.
    @Test
    void verifyConsumedTokenThrows() {
        EmailVerificationToken token = tokenExpiringAt(NOW.plus(Duration.ofHours(1)));
        token.setConsumedAt(NOW.minus(Duration.ofMinutes(5)));
        when(tokens.findByToken("tok")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> serviceAt(NOW).verify("tok"))
                .isInstanceOf(TokenInvalidException.class);
        assertThat(token.getUser().isEmailVerified()).isFalse();
    }

    // Boundary: just-before-expiry is accepted, exactly-at-expiry is rejected.
    @Test
    void verifyExpiryBoundary() {
        Instant expiresAt = NOW.plus(TTL);
        when(tokens.findByToken("tok")).thenReturn(Optional.of(tokenExpiringAt(expiresAt)));

        // 1ms before expiry → accepted.
        assertThat(serviceAt(expiresAt.minusMillis(1)).verify("tok").isEmailVerified()).isTrue();

        // exactly at expiry → rejected (expiry is exclusive).
        when(tokens.findByToken("tok")).thenReturn(Optional.of(tokenExpiringAt(expiresAt)));
        assertThatThrownBy(() -> serviceAt(expiresAt).verify("tok"))
                .isInstanceOf(TokenInvalidException.class);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }
}
