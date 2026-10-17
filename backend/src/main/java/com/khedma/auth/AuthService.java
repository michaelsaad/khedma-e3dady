package com.khedma.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.khedma.auth.AuthDtos.AuthResponse;
import com.khedma.auth.AuthDtos.LoginChallenge;
import com.khedma.auth.AuthDtos.LoginRequest;
import com.khedma.auth.AuthDtos.RegisterRequest;
import com.khedma.auth.AuthDtos.RegistrationResponse;
import com.khedma.auth.GoogleTokenVerifier.GoogleProfile;
import com.khedma.auth.otp.CodePurpose;
import com.khedma.auth.otp.OtpService;
import com.khedma.common.ApiException;
import com.khedma.common.ErrorCode;
import com.khedma.config.AppProperties;
import com.khedma.security.RateLimiter;
import com.khedma.user.AppUser;
import com.khedma.user.Role;
import com.khedma.user.UserRepository;
import com.khedma.user.UserResponse;
import com.khedma.user.UserStatus;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleTokenVerifier googleVerifier;
    private final OtpService otpService;
    private final TrustedDeviceService trustedDevices;
    private final RateLimiter limiter;
    private final AppProperties properties;
    private final Clock clock;
    /** Compared against when the email is unknown, so a wrong email takes as long as a wrong password. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       GoogleTokenVerifier googleVerifier, OtpService otpService, TrustedDeviceService trustedDevices,
                       RateLimiter limiter, AppProperties properties, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.googleVerifier = googleVerifier;
        this.otpService = otpService;
        this.trustedDevices = trustedDevices;
        this.limiter = limiter;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
    }

    /**
     * Creates an unverified account and emails a code. Registering again with an email that was
     * never verified replaces the earlier attempt, so nobody can squat an address they don't own.
     */
    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        AppUser user = users.findByEmail(email).orElseGet(AppUser::new);
        if (user.isEmailVerified() || user.getGoogleSubject() != null) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        user.setEmail(email);
        user.setDisplayName(request.displayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user = users.save(user);
        Instant expiresAt = otpService.issue(user);
        return new RegistrationResponse(email, expiresAt);
    }

    /** Wrong codes are counted as attempts, so this transaction must commit even when it throws. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse verifyEmail(String rawEmail, String code, String userAgent) {
        AppUser user = users.findByEmail(normalizeEmail(rawEmail))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_VERIFICATION_CODE));
        if (user.isEmailVerified()) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_VERIFIED);
        }
        otpService.verify(user, code, CodePurpose.VERIFY_EMAIL);
        user.setEmailVerified(true);
        // Reading the code proved the inbox, which is exactly what the sign-in code checks.
        return completeLogin(user, trustedDevices.trust(user, userAgent));
    }

    /** Silently does nothing for unknown or already-verified emails, to avoid leaking which exist. */
    @Transactional
    public void resendCode(String rawEmail) {
        users.findByEmail(normalizeEmail(rawEmail))
                .filter(user -> !user.isEmailVerified() && user.getPasswordHash() != null)
                .ifPresent(otpService::issue);
    }

    /**
     * Emails a password reset code. Unknown emails get the same response, so the endpoint
     * doesn't reveal who has an account. Works for any account, including Google-only and
     * imported ones, because the code proves the person controls the inbox.
     */
    @Transactional
    public void forgotPassword(String rawEmail) {
        users.findByEmail(normalizeEmail(rawEmail))
                .ifPresent(user -> otpService.issue(user, CodePurpose.RESET_PASSWORD));
    }

    /**
     * Sets the new password, signs out every other session and forgets every trusted browser, then
     * signs this one in. Wrong codes are counted, so this transaction must commit even when it throws.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse resetPassword(String rawEmail, String code, String newPassword, String userAgent) {
        AppUser user = users.findByEmail(normalizeEmail(rawEmail))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_VERIFICATION_CODE));
        otpService.verify(user, code, CodePurpose.RESET_PASSWORD);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setEmailVerified(true); // reading the code proves the inbox is theirs
        user.revokeTokens();
        trustedDevices.forgetAll(user.getId());
        limiter.reset(loginFailuresKey(user.getEmail()));
        return completeLogin(user, trustedDevices.trust(user, userAgent));
    }

    /**
     * Step 1 of an email/password sign-in. A trusted browser gets its access token right away; any
     * other gets a challenge and an emailed code (step 2: {@link #verifyLoginCode}).
     */
    @Transactional
    public Object login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        AppProperties.RateLimit limits = properties.rateLimit();
        String failuresKey = loginFailuresKey(email);
        if (limits.enabled() && limiter.isBlocked(failuresKey, limits.loginFailuresPerEmail(), limits.loginWindow())) {
            throw new ApiException(ErrorCode.RATE_LIMITED);
        }
        AppUser user = users.findByEmail(email).filter(found -> found.getPasswordHash() != null).orElse(null);
        boolean matches = passwordEncoder.matches(request.password(), user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !matches) {
            if (limits.enabled()) limiter.tryAcquire(failuresKey, limits.loginFailuresPerEmail(), limits.loginWindow());
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!user.isEmailVerified()) {
            throw new ApiException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        limiter.reset(failuresKey);
        if (!properties.twoFactor().enabled() || trustedDevices.isTrusted(user, request.deviceToken())) {
            return completeLogin(user, null);
        }
        sendLoginCode(user);
        JwtService.IssuedToken challenge = jwtService.issueLoginChallenge(user, properties.twoFactor().challengeTtl());
        return new LoginChallenge(challenge.token(), user.getEmail(), challenge.expiresAt());
    }

    /** Step 2: the emailed code plus the challenge from step 1. Trusts this browser from now on. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse verifyLoginCode(String challengeToken, String code, String userAgent) {
        AppUser user = challengedUser(challengeToken);
        otpService.verify(user, code, CodePurpose.LOGIN);
        return completeLogin(user, trustedDevices.trust(user, userAgent));
    }

    @Transactional
    public void resendLoginCode(String challengeToken) {
        otpService.issue(challengedUser(challengeToken), CodePurpose.LOGIN);
    }

    @Transactional
    public AuthResponse loginWithGoogle(String idToken) {
        GoogleProfile profile = googleVerifier.verify(idToken);
        String email = normalizeEmail(profile.email());

        // Match by Google account first, then link an existing email/password account.
        AppUser user = users.findByGoogleSubject(profile.subject())
                .or(() -> users.findByEmail(email))
                .orElseGet(() -> {
                    AppUser created = new AppUser();
                    created.setEmail(email);
                    created.setDisplayName(profile.name() != null && !profile.name().isBlank() ? profile.name() : email);
                    return created;
                });
        if (!user.isEmailVerified()) {
            // A password set during an unverified signup may belong to someone who doesn't own this
            // inbox. Google just proved ownership, so drop that password and any pending code.
            user.setPasswordHash(null);
            if (user.getId() != null) otpService.discard(user.getId());
            user.setEmailVerified(true);
        }
        if (user.getGoogleSubject() == null) {
            user.setGoogleSubject(profile.subject());
        }
        if (profile.pictureUrl() != null && profile.pictureUrl().length() <= 500) {
            user.setAvatarUrl(profile.pictureUrl());
        }
        return completeLogin(users.save(user), null);
    }

    /** Ends every session of the account: all access tokens and all trusted browsers. */
    @Transactional
    public void signOutEverywhere(Long userId) {
        AppUser user = users.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        user.revokeTokens();
        trustedDevices.forgetAll(userId);
    }

    private void sendLoginCode(AppUser user) {
        try {
            otpService.issue(user, CodePurpose.LOGIN);
        } catch (ApiException ex) {
            // Signing in twice within the resend cooldown: the code from a moment ago is still valid.
            if (ex.getCode() != ErrorCode.RESEND_TOO_SOON) throw ex;
        }
    }

    private AppUser challengedUser(String challengeToken) {
        JwtService.Challenge challenge = jwtService.readLoginChallenge(challengeToken);
        AppUser user = users.findById(challenge.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.LOGIN_CHALLENGE_INVALID));
        if (user.getTokenVersion() != challenge.tokenVersion()) {
            throw new ApiException(ErrorCode.LOGIN_CHALLENGE_INVALID);
        }
        return user;
    }

    private AuthResponse completeLogin(AppUser user, String deviceToken) {
        // Owner emails are promoted only once the address is proven (OTP or Google).
        if (user.isEmailVerified() && properties.normalizedSuperAdminEmails().contains(user.getEmail())) {
            user.setRole(Role.SUPER_ADMIN);
            user.setStatus(UserStatus.APPROVED);
        }
        user.setLastLoginAt(clock.instant());
        JwtService.IssuedToken token = jwtService.issue(user);
        return new AuthResponse(token.token(), token.expiresAt(), UserResponse.of(user), deviceToken);
    }

    private static String loginFailuresKey(String email) {
        return "login-fail:" + email;
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
