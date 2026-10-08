package com.khedma.auth;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import com.khedma.common.ApiException;
import com.khedma.config.AppProperties;

/**
 * Verifies Google ID tokens (from Google Identity Services on the frontend)
 * against Google's public keys, issuer and our OAuth client id.
 */
@Component
public class GoogleTokenVerifier {

    private static final String GOOGLE_JWKS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> GOOGLE_ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final AppProperties properties;
    private volatile NimbusJwtDecoder decoder;

    public GoogleTokenVerifier(AppProperties properties) {
        this.properties = properties;
    }

    public GoogleProfile verify(String idToken) {
        if (!properties.google().enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "تسجيل الدخول بجوجل غير مفعّل");
        }
        Jwt jwt;
        try {
            jwt = decoder().decode(idToken);
        } catch (JwtException ex) {
            throw ApiException.unauthorized("تعذر التحقق من حساب جوجل");
        }
        String email = jwt.getClaimAsString("email");
        if (email == null || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            throw ApiException.unauthorized("إيميل جوجل غير مؤكد");
        }
        return new GoogleProfile(
                jwt.getSubject(),
                email,
                jwt.getClaimAsString("name"),
                jwt.getClaimAsString("picture"));
    }

    private NimbusJwtDecoder decoder() {
        if (decoder == null) {
            synchronized (this) {
                if (decoder == null) {
                    NimbusJwtDecoder built = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS).build();
                    String clientId = properties.google().clientId();
                    OAuth2TokenValidator<Jwt> issuer = jwt -> GOOGLE_ISSUERS.contains(jwt.getClaimAsString("iss"))
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Bad issuer", null));
                    OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(clientId)
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Bad audience", null));
                    built.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), issuer, audience));
                    decoder = built;
                }
            }
        }
        return decoder;
    }

    public record GoogleProfile(String subject, String email, String name, String pictureUrl) {}
}
