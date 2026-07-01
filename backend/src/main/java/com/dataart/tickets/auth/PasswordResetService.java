package com.dataart.tickets.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Password reset (HTS-037, architecture.md §9/§10): issue a single-use, short-lived token and
 * email a reset link (Mailpit in dev), then validate/consume the token to set a new Argon2id hash.
 * Mirrors {@link EmailVerificationService} but for a distinct token type/purpose.
 *
 * <p>No account enumeration: {@link #requestReset(String)} behaves identically whether or not the
 * email exists (the controller always returns the same generic 202), so the endpoint cannot be
 * used to probe which addresses are registered.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final int TOKEN_BYTES = 32;

    private final PasswordResetTokenRepository tokens;
    private final UserRepository users;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Duration tokenTtl;
    private final String baseUrl;
    private final String mailFrom;

    public PasswordResetService(
            PasswordResetTokenRepository tokens,
            UserRepository users,
            JavaMailSender mailSender,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${app.password-reset.token-ttl}") Duration tokenTtl,
            @Value("${app.base-url}") String baseUrl,
            @Value("${app.mail.from}") String mailFrom) {
        this.tokens = tokens;
        this.users = users;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.tokenTtl = tokenTtl;
        this.baseUrl = baseUrl;
        this.mailFrom = mailFrom;
    }

    /**
     * Request a password reset (FR-A10 pattern). If a matching account exists, its outstanding
     * unused reset tokens are invalidated and a fresh single-use token is issued and emailed.
     * Returns silently regardless of whether the email exists, so the endpoint cannot be used to
     * enumerate accounts. The email send is best-effort — a token is always persisted first.
     */
    @Transactional
    public void requestReset(String rawEmail) {
        String email = EmailNormalizer.normalize(rawEmail);
        users.findByEmail(email).ifPresent(user -> {
            Instant now = clock.instant();
            // Invalidate prior unused tokens so only the newest can reset.
            for (PasswordResetToken prior : tokens.findByUserAndConsumedAtIsNull(user)) {
                prior.setConsumedAt(now);
            }
            String tokenValue = generateToken();
            tokens.save(new PasswordResetToken(user, tokenValue, now.plus(tokenTtl)));
            try {
                sendResetEmail(user.getEmail(), tokenValue);
            } catch (MailException e) {
                log.warn("Password reset email to {} could not be sent; token still issued.",
                        user.getEmail(), e);
            }
        });
    }

    /**
     * Validate and consume a reset token, setting a new Argon2id password hash. The token must
     * exist, be unconsumed, and be unexpired. On success the user's password is replaced, the token
     * is consumed, and any other outstanding reset tokens for that user are invalidated too.
     *
     * @throws TokenInvalidException if the token is unknown, consumed, or expired
     */
    @Transactional
    public void reset(String tokenValue, String newPassword) {
        PasswordResetToken token = tokens.findByToken(tokenValue)
                .orElseThrow(() -> new TokenInvalidException(
                        "The password reset link is invalid or has expired."));

        Instant now = clock.instant();
        if (token.isConsumed() || token.isExpiredAt(now)) {
            throw new TokenInvalidException("The password reset link is invalid or has expired.");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        token.setConsumedAt(now);
        // Belt-and-braces: invalidate any other outstanding tokens for this user.
        for (PasswordResetToken other : tokens.findByUserAndConsumedAtIsNull(user)) {
            other.setConsumedAt(now);
        }
    }

    private void sendResetEmail(String to, String tokenValue) {
        String link = baseUrl + "/reset-password?token=" + tokenValue;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to);
        message.setSubject("Reset your password");
        message.setText(
                "We received a request to reset your password. Open this link to choose a new one:"
                        + "\n\n" + link
                        + "\n\nIf you did not request this, you can ignore this email."
                        + "\nThis link expires in "
                        + Math.max(1, tokenTtl.toHours()) + " hour(s).");
        mailSender.send(message);
    }

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return TOKEN_ENCODER.encodeToString(bytes);
    }
}
