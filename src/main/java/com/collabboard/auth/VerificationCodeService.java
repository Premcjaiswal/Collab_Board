package com.collabboard.auth;

import com.collabboard.common.ApiException;
import com.collabboard.config.AppProperties;
import com.collabboard.notification.EmailService;
import com.collabboard.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Issues and checks short-lived, single-use 6-digit codes for email verification and password reset.
 * Codes are stored only as HMAC-SHA256 digests keyed with a server secret.
 */
@Service
public class VerificationCodeService {

    private static final Logger log = LoggerFactory.getLogger(VerificationCodeService.class);

    private final VerificationCodeRepository repository;
    private final EmailService emailService;
    private final AppProperties.Security config;
    private final SecureRandom random = new SecureRandom();
    private final byte[] secret;

    public VerificationCodeService(VerificationCodeRepository repository, EmailService emailService, AppProperties props) {
        this.repository = repository;
        this.emailService = emailService;
        this.config = props.security();
        String configured = config.otpSecret();
        if (configured == null || configured.isBlank()) {
            byte[] generated = new byte[32];
            random.nextBytes(generated);
            this.secret = generated;
            log.warn("OTP_SECRET is not set; using a random per-process secret. Outstanding codes become invalid on restart.");
        } else {
            this.secret = configured.getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * Enforces resend cooldown and hourly limits, invalidates older codes, stores a new one and emails it.
     *
     * @return whether the email was handed to the mail server
     */
    public boolean issue(User user, CodePurpose purpose) {
        Instant now = Instant.now();
        repository.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(user.getId(), purpose).ifPresent(last -> {
            long elapsed = Duration.between(last.getCreatedAt(), now).toSeconds();
            long wait = config.otpResendCooldownSeconds() - elapsed;
            if (wait > 0) {
                throw ApiException.tooManyRequests("Please wait " + wait + " seconds before requesting another code.");
            }
        });
        long recent = repository.countByUserIdAndPurposeAndCreatedAtAfter(user.getId(), purpose, now.minus(Duration.ofHours(1)));
        if (recent >= config.otpMaxSendsPerHour()) {
            throw ApiException.tooManyRequests("Too many codes requested. Please try again later.");
        }
        repository.findByUserIdAndPurposeAndConsumedAtIsNull(user.getId(), purpose).forEach(c -> c.consume(now));

        String code = String.format("%06d", random.nextInt(1_000_000));
        repository.save(new VerificationCode(user, purpose, hash(code), now,
                now.plus(Duration.ofMinutes(config.otpTtlMinutes()))));

        String subject;
        String intro;
        if (purpose == CodePurpose.EMAIL_VERIFICATION) {
            subject = "Verify your CollabBoard email";
            intro = "Use this code to verify your CollabBoard account email address:";
        } else {
            subject = "Reset your CollabBoard password";
            intro = "Use this code to reset your CollabBoard password:";
        }
        String body = "Hello " + user.getFullName() + ",\n\n" + intro + "\n\n    " + code + "\n\n"
                + "The code expires in " + config.otpTtlMinutes() + " minutes and can be used once.\n"
                + "If you did not request this, you can ignore this email.\n\n— CollabBoard";
        return emailService.send(user.getEmail(), subject, body, "purpose=" + purpose + " code=" + code);
    }

    /**
     * Checks a code. Failed attempts are counted; callers must not roll back on {@link ApiException}
     * so the attempt counter is persisted.
     */
    public void verify(User user, CodePurpose purpose, String code) {
        Instant now = Instant.now();
        VerificationCode latest = repository.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(user.getId(), purpose)
                .orElseThrow(VerificationCodeService::invalid);
        if (latest.getConsumedAt() != null || latest.getExpiresAt().isBefore(now)) {
            throw invalid();
        }
        if (latest.getAttempts() >= config.otpMaxAttempts()) {
            throw ApiException.tooManyRequests("Too many incorrect attempts. Please request a new code.");
        }
        byte[] expected = latest.getCodeHash().getBytes(StandardCharsets.UTF_8);
        byte[] actual = hash(code == null ? "" : code.trim()).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            latest.incrementAttempts();
            repository.save(latest);
            throw invalid();
        }
        latest.consume(now);
        repository.save(latest);
    }

    private static ApiException invalid() {
        return ApiException.badRequest("INVALID_CODE", "The code is invalid or has expired.");
    }

    private String hash(String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }
}
