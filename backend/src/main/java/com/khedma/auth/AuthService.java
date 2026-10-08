package com.khedma.auth;

import java.time.Clock;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.khedma.auth.AuthDtos.AuthResponse;
import com.khedma.auth.AuthDtos.LoginRequest;
import com.khedma.auth.AuthDtos.RegisterRequest;
import com.khedma.auth.GoogleTokenVerifier.GoogleProfile;
import com.khedma.common.ApiException;
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
    private final AppProperties properties;
    private final Clock clock;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       GoogleTokenVerifier googleVerifier, AppProperties properties, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.googleVerifier = googleVerifier;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.findByEmail(email).isPresent()) {
            throw ApiException.conflict("الإيميل مسجل بالفعل، سجّل الدخول");
        }
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setDisplayName(request.displayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        return completeLogin(users.save(user));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        AppUser user = users.findByEmail(normalizeEmail(request.email()))
                .filter(found -> found.getPasswordHash() != null)
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("الإيميل أو كلمة السر غير صحيحة"));
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
        if (user.getGoogleSubject() == null) {
            // Email/password signups are not email-verified, so whoever set that password may not own
            // this inbox. Google just proved ownership: drop the unverified password on first link.
            user.setPasswordHash(null);
            user.setGoogleSubject(profile.subject());
        }
        if (profile.pictureUrl() != null && profile.pictureUrl().length() <= 500) {
            user.setAvatarUrl(profile.pictureUrl());
        }
        // Bootstrap super admins only through a verified Google email, never through password signup.
        if (properties.normalizedSuperAdminEmails().contains(email)) {
            user.setRole(Role.SUPER_ADMIN);
            user.setStatus(UserStatus.APPROVED);
        }
        return completeLogin(users.save(user));
    }

    private AuthResponse completeLogin(AppUser user) {
        user.setLastLoginAt(clock.instant());
        JwtService.IssuedToken token = jwtService.issue(user);
        return new AuthResponse(token.token(), token.expiresAt(), UserResponse.of(user));
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
