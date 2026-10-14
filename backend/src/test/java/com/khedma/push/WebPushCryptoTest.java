package com.khedma.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class WebPushCryptoTest {

    private static final Base64.Decoder DECODE = Base64.getUrlDecoder();
    private static final Base64.Encoder ENCODE = Base64.getUrlEncoder().withoutPadding();

    /**
     * The worked example of RFC 8291 section 5: same inputs must give the same bytes. (The ciphertext printed in
     * appendix A differs from section 5; section 5 is the message the RFC actually shows on the wire.)
     */
    @Test
    void matchesTheRfc8291TestVector() throws Exception {
        byte[] asPublic = DECODE.decode("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
        byte[] asPrivate = DECODE.decode("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw");
        byte[] uaPublic = DECODE.decode("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
        byte[] authSecret = DECODE.decode("BTBZMqHH6r4Tts7J_aSIgg");
        byte[] salt = DECODE.decode("DGv6ra1nlYgDCS1FRnbzlw");
        byte[] plaintext = DECODE.decode("V2hlbiBJIGdyb3cgdXAsIEkgd2FudCB0byBiZSBhIHdhdGVybWVsb24");

        KeyPair applicationServer = new KeyPair(EcKeys.publicKey(asPublic), EcKeys.privateKey(asPrivate));
        byte[] body = WebPushCrypto.encrypt(plaintext, uaPublic, authSecret, applicationServer, salt);

        String expected = "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A"
                + "_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";
        assertThat(ENCODE.encodeToString(body)).isEqualTo(expected);
    }

    @Test
    void freshKeysAndSaltEveryTime() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        byte[] uaPublic = EcKeys.encode((ECPublicKey) generator.generateKeyPair().getPublic());
        byte[] auth = new byte[16];
        byte[] message = "مرحبا".getBytes(StandardCharsets.UTF_8);

        byte[] first = WebPushCrypto.encrypt(message, uaPublic, auth);
        byte[] second = WebPushCrypto.encrypt(message, uaPublic, auth);

        assertThat(first).isNotEqualTo(second);
        assertThat(first[20]).isEqualTo((byte) 65); // idlen: the 65-byte sender key follows the header
    }

    @Test
    void vapidTokenIsAValidEs256Jwt() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keys = generator.generateKeyPair();
        String publicKey = ENCODE.encodeToString(EcKeys.encode((ECPublicKey) keys.getPublic()));
        // Raw 32-byte private scalar, as `web-push generate-vapid-keys` prints it.
        byte[] raw = ((java.security.interfaces.ECPrivateKey) keys.getPrivate()).getS().toByteArray();
        byte[] scalar = new byte[32];
        System.arraycopy(raw, Math.max(0, raw.length - 32), scalar, 32 - Math.min(32, raw.length), Math.min(32, raw.length));

        VapidSigner signer = new VapidSigner(publicKey, ENCODE.encodeToString(scalar), "mailto:test@khedma.local");
        String header = signer.authorization("https://fcm.googleapis.com/fcm/send/abc", Instant.parse("2026-10-14T10:00:00Z"));

        assertThat(header).startsWith("vapid t=").endsWith(", k=" + publicKey);
        String jwt = header.substring("vapid t=".length(), header.indexOf(", k="));
        String[] parts = jwt.split("\\.");
        assertThat(new String(DECODE.decode(parts[0]), StandardCharsets.UTF_8)).contains("\"alg\":\"ES256\"");
        String claims = new String(DECODE.decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(claims).contains("\"aud\":\"https://fcm.googleapis.com\"").contains("\"sub\":\"mailto:test@khedma.local\"");

        Signature verify = Signature.getInstance("SHA256withECDSAinP1363Format");
        verify.initVerify(keys.getPublic());
        verify.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verify.verify(DECODE.decode(parts[2]))).isTrue();
    }
}
