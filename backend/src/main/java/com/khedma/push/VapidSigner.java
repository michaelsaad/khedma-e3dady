package com.khedma.push;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** VAPID (RFC 8292): proves to the push service which application server is sending, with an ES256 JWT. */
final class VapidSigner {

    private static final Base64.Encoder B64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final ObjectMapper JSON = new ObjectMapper();
    /** Push services accept at most 24 hours. */
    private static final Duration VALID_FOR = Duration.ofHours(12);

    private final PrivateKey privateKey;
    private final String publicKey;
    private final String subject;

    /** @param publicKey the uncompressed public key, base64url (also given to browsers as applicationServerKey) */
    VapidSigner(String publicKey, String privateKey, String subject) throws GeneralSecurityException {
        this.publicKey = publicKey.trim();
        this.privateKey = EcKeys.privateKey(EcKeys.decodeBase64Url(privateKey));
        this.subject = subject.trim();
    }

    /** The {@code Authorization} header value for a push to {@code endpoint}. */
    String authorization(String endpoint, Instant now) throws GeneralSecurityException {
        URI uri = URI.create(endpoint);
        String audience = uri.getScheme() + "://" + uri.getAuthority();

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("aud", audience);
        claims.put("exp", now.plus(VALID_FOR).getEpochSecond());
        claims.put("sub", subject);

        String signingInput = encode(Map.of("typ", "JWT", "alg", "ES256")) + "." + encode(claims);
        Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
        signature.initSign(privateKey);
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return "vapid t=" + signingInput + "." + B64_URL.encodeToString(signature.sign()) + ", k=" + publicKey;
    }

    private static String encode(Map<String, Object> json) {
        try {
            return B64_URL.encodeToString(JSON.writeValueAsBytes(json));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
