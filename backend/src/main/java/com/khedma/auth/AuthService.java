package com.khedma.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.khedma.auth.AuthDtos.AuthResponse;
import com.khedma.auth.AuthDtos.LoginRequest;
import com.khedma.auth.AuthDtos.RegisterRequest;
import com.khedma.auth.AuthDtos.RegistrationResponse;
import com.khedma.auth.GoogleTokenVerifier.GoogleProfile;
import com.khedma.auth.otp.OtpService;
import com.khedma.common.ApiException;
import com.khedma.common.ErrorCode;
import com.khedma.config.AppProperties;
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
    private final AppProperties properties;
    private final Clock clock;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       GoogleTokenVerifier googleVerifier, OtpService otpService,
                       AppProperties properties, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.googleVerifier = googleVerifier;
        this.otpService = otpService;
        this.properties = properties;
        this.clock = clock;
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
    public AuthResponse verifyEmail(String rawEmail, String code) {
        AppUser user = users.findByEmail(normalizeEmail(rawEmail))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_VERIFICATION_CODE));
        if (user.isEmailVerified()) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_VERIFIED);
        }
        otpService.verify(user, code);
        user.setEmailVerified(true);
        return completeLogin(user);
    }

    /** Silently does nothing for unknown or already-verified emails, to avoid leaking which exist. */
    @Transactional
    public void resendCode(String rawEmail) {
        users.findByEmail(normalizeEmail(rawEmail))
                .filter(user -> !user.isEmailVerified() && user.getPasswordHash() != null)
                .ifPresent(otpService::issue);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        AppUser user = users.findByEmail(normalizeEmail(request.email()))
                .filter(found -> found.getPasswordHash() != null)
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));
        if (!user.isEmailVerified()) {
            throw new ApiException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        return completeLogin(user);
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
        return completeLogin(users.save(user));
    }

    private AuthResponse completeLogin(AppUser user) {
        // Owner emails are promoted only once the address is proven (OTP or Google).
        if (user.isEmailVerified() && properties.normalizedSuperAdminEmails().contains(user.getEmail())) {
            user.setRole(Role.SUPER_ADMIN);
            user.setStatus(UserStatus.APPROVED);
        }
        user.setLastLoginAt(clock.instant());
        JwtService.IssuedToken token = jwtService.issue(user);
        return new AuthResponse(token.token(), token.expiresAt(), UserResponse.of(user));
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
