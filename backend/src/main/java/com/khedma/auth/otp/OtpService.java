package com.khedma.auth.otp;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.khedma.common.ApiException;
import com.khedma.common.ErrorCode;
import com.khedma.config.AppProperties;
import com.khedma.user.AppUser;

/**
 * 6-digit email codes: stored hashed, short-lived, bound to one purpose. Besides the per-code attempt
 * limit, an account gets a capped number of codes per day and of wrong guesses per hour, so asking for
 * new codes can't be used to keep guessing.
 */
@Service
public class OtpService {

    private static final Duration SEND_WINDOW = Duration.ofDays(1);
    private static final Duration FAIL_WINDOW = Duration.ofHours(1);

    private final SecureRandom random = new SecureRandom();
    private final EmailVerificationCodeRepository codes;
    private final EmailSender emailSender;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties.Otp settings;
    private final Clock clock;

    public OtpService(EmailVerificationCodeRepository codes, EmailSender emailSender,
                      PasswordEncoder passwordEncoder, AppProperties properties, Clock clock) {
        this.codes = codes;
        this.emailSender = emailSender;
        this.passwordEncoder = passwordEncoder;
        this.settings = properties.otp();
        this.clock = clock;
    }

    /** Creates (or replaces) the user's email verification code and emails it. Returns when it expires. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Instant issue(AppUser user) {
        return issue(user, CodePurpose.VERIFY_EMAIL);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Instant issue(AppUser user, CodePurpose purpose) {
        Instant now = clock.instant();
        EmailVerificationCode entry = codes.findById(user.getId()).orElseGet(() -> {
            EmailVerificationCode created = new EmailVerificationCode();
            created.setUserId(user.getId());
            return created;
        });
        if (entry.getLastSentAt() != null && entry.getLastSentAt().plus(settings.resendCooldown()).isAfter(now)) {
            throw new ApiException(ErrorCode.RESEND_TOO_SOON);
        }
        if (expired(entry.getSendWindowStart(), SEND_WINDOW, now)) {
            entry.setSendWindowStart(now);
            entry.setSendsInWindow(0);
        }
        if (entry.getSendsInWindow() >= settings.maxSendsPerDay()) {
            throw new ApiException(ErrorCode.TOO_MANY_CODES);
        }
        String code = "%06d".formatted(random.nextInt(1_000_000));
        entry.setCodeHash(passwordEncoder.encode(code));
        entry.setPurpose(purpose);
        entry.setExpiresAt(now.plus(settings.ttl()));
        entry.setAttempts(0);
        entry.setLastSentAt(now);
        entry.setSendsInWindow(entry.getSendsInWindow() + 1);
        codes.save(entry);
        emailSender.sendCode(user.getEmail(), user.getDisplayName(), code, settings.ttl(), purpose);
        return entry.getExpiresAt();
    }

    /**
     * Checks the code and consumes it on success. A wrong code counts as an attempt, so callers
     * must not roll back on {@link ApiException} (see AuthService#verifyEmail).
     */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = ApiException.class)
    public void verify(AppUser user, String code, CodePurpose purpose) {
        Instant now = clock.instant();
        EmailVerificationCode entry = codes.findById(user.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_VERIFICATION_CODE));
        if (expired(entry.getFailWindowStart(), FAIL_WINDOW, now)) {
            entry.setFailWindowStart(now);
            entry.setFailuresInWindow(0);
        }
        if (entry.getAttempts() >= settings.maxAttempts() || entry.getFailuresInWindow() >= settings.maxFailuresPerHour()) {
            throw new ApiException(ErrorCode.TOO_MANY_ATTEMPTS);
        }
        if (entry.getPurpose() != purpose) {
            // Not this flow's code: same answer as a wrong code, and it doesn't burn an attempt.
            throw new ApiException(ErrorCode.INVALID_VERIFICATION_CODE);
        }
        if (entry.getExpiresAt().isBefore(now)) {
            throw new ApiException(ErrorCode.VERIFICATION_CODE_EXPIRED);
        }
        if (!passwordEncoder.matches(code, entry.getCodeHash())) {
            entry.setAttempts(entry.getAttempts() + 1);
            entry.setFailuresInWindow(entry.getFailuresInWindow() + 1);
            throw new ApiException(ErrorCode.INVALID_VERIFICATION_CODE);
        }
        codes.delete(entry);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void discard(Long userId) {
        codes.findById(userId).ifPresent(codes::delete);
    }

    private static boolean expired(Instant windowStart, Duration window, Instant now) {
        return windowStart == null || !windowStart.plus(window).isAfter(now);
    }
}
