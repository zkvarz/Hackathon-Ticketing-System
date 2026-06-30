package com.dataart.tickets.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Email verification (HTS-007, architecture.md §9/§10): issue a single-use token, email a
 * verification link via the configurable SMTP service (Mailpit in dev), and verify/consume the
 * token. 24h TTL (FR-A8); successful verification does not create a session (FR-A9).
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final int TOKEN_BYTES = 32;

    private final EmailVerificationTokenRepository tokens;
    private final JavaMailSender mailSender;
    private final Clock clock;
    private final Duration tokenTtl;
    private final String baseUrl;
    private final String mailFrom;

    public EmailVerificationService(
            EmailVerificationTokenRepository tokens,
            JavaMailSender mailSender,
            Clock clock,
            @Value("${app.verification.token-ttl}") Duration tokenTtl,
            @Value("${app.base-url}") String baseUrl,
            @Value("${app.mail.from}") String mailFrom) {
        this.tokens = tokens;
        this.mailSender = mailSender;
        this.clock = clock;
        this.tokenTtl = tokenTtl;
        this.baseUrl = baseUrl;
        this.mailFrom = mailFrom;
    }

    /**
     * Issue a fresh token for the user and email the verification link. The token is always
     * persisted; the email send is best-effort — if SMTP is unreachable the account still
     * exists and the user can request a resend (FR-A10), rather than the whole signup failing.
     */
    @Transactional
    public EmailVerificationToken issueAndSend(User user) {
        Instant now = clock.instant();
        String tokenValue = generateToken();
        EmailVerificationToken token =
                tokens.save(new EmailVerificationToken(user, tokenValue, now.plus(tokenTtl)));

        try {
            sendVerificationEmail(user.getEmail(), tokenValue);
        } catch (MailException e) {
            log.warn("Verification email to {} could not be sent; token still issued.",
                    user.getEmail(), e);
        }
        return token;
    }

    /**
     * Validate and consume a token: it must exist, be unconsumed, and be unexpired. On success
     * the user is marked verified and the token consumed.
     *
     * @return the now-verified user
     * @throws TokenInvalidException if the token is unknown, consumed, or expired
     */
    @Transactional
    public User verify(String tokenValue) {
        EmailVerificationToken token = tokens.findByToken(tokenValue)
                .orElseThrow(TokenInvalidException::new);

        Instant now = clock.instant();
        if (token.isConsumed() || token.isExpiredAt(now)) {
            throw new TokenInvalidException();
        }

        token.getUser().setEmailVerified(true);
        token.setConsumedAt(now);
        return token.getUser();
    }

    private void sendVerificationEmail(String to, String tokenValue) {
        String link = baseUrl + "/verify?token=" + tokenValue;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to);
        message.setSubject("Verify your email");
        message.setText(
                "Welcome! Please verify your email address by opening this link:\n\n" + link
                        + "\n\nThis link expires in 24 hours.");
        mailSender.send(message);
    }

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return TOKEN_ENCODER.encodeToString(bytes);
    }
}
