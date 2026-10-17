package com.khedma.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import com.khedma.common.ApiException;
import com.khedma.common.ErrorCode;
import com.khedma.config.AppProperties;
import com.khedma.user.AppUser;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Issues and verifies this backend's own tokens (HS256): access tokens, and short-lived sign-in
 * challenges for the emailed second step. A challenge is marked with a {@code typ} claim so it can
 * never be used as an access token.
 */
@Service
public class JwtService {

    private static final String ISSUER = "khedma";
    static final String VERSION_CLAIM = "ver";
    static final String TYPE_CLAIM = "typ";
    private static final String LOGIN_CHALLENGE = "login-2fa";
    private static final String DEV_SECRET = "local-dev-secret-change-me-local-dev-secret";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final AppProperties properties;
    private final Clock clock;

    public JwtService(AppProperties properties, Clock clock) {
        String configured = properties.jwt().secret();
        if (DEV_SECRET.equals(configured) && System.getenv("RENDER") != null) {
            // Anyone could forge tokens with the published development secret.
            throw new IllegalStateException("JWT_SECRET must be set in production");
        }
        byte[] secret = configured.getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes");
        }
        SecretKey key = new SecretKeySpec(secret, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        // Expiry is checked against the app clock (the same one that set it), not the JVM wall clock.
        JwtTimestampValidator timestamps = new JwtTimestampValidator();
        timestamps.setClock(clock);
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(ISSUER)));
        this.decoder = nimbus;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedToken issue(AppUser user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.jwt().ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("email", user.getEmail())
                .claim(VERSION_CLAIM, user.getTokenVersion())
                .build();
        return new IssuedToken(encode(claims), expiresAt);
    }

    /** Proves the password was right; exchanged for an access token together with the emailed code. */
    public IssuedToken issueLoginChallenge(AppUser user, Duration ttl) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(TYPE_CLAIM, LOGIN_CHALLENGE)
                .claim(VERSION_CLAIM, user.getTokenVersion())
                .build();
        return new IssuedToken(encode(claims), expiresAt);
    }

    /** Returns the user id and token version of a valid, unexpired challenge. */
    public Challenge readLoginChallenge(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            if (!LOGIN_CHALLENGE.equals(jwt.getClaimAsString(TYPE_CLAIM))) {
                throw new ApiException(ErrorCode.LOGIN_CHALLENGE_INVALID);
            }
            return new Challenge(Long.valueOf(jwt.getSubject()), versionOf(jwt));
        } catch (JwtException | NumberFormatException ex) {
            throw new ApiException(ErrorCode.LOGIN_CHALLENGE_INVALID);
        }
    }

    public JwtDecoder decoder() {
        return decoder;
    }

    /** Tokens issued before versioning existed carry no claim and count as version 0. */
    static int versionOf(Jwt jwt) {
        Object value = jwt.getClaims().get(VERSION_CLAIM);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public record IssuedToken(String token, Instant expiresAt) {}

    public record Challenge(Long userId, int tokenVersion) {}
}
