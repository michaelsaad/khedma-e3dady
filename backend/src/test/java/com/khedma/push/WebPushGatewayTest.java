package com.khedma.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.khedma.push.PushGateway.Outcome;
import com.khedma.push.PushGateway.Target;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Sends through the real {@link WebPushGateway} to a local server that plays the push service, then opens the
 * message the way a browser would: if this passes, the request is well-formed and decryptable.
 */
class WebPushGatewayTest {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder UNB64 = Base64.getUrlDecoder();

    private HttpServer server;
    private int status = 201;
    private final AtomicReference<Captured> captured = new AtomicReference<>();

    private KeyPair vapid;
    private KeyPair receiver;
    private byte[] authSecret;
    private WebPushGateway gateway;
    private Target target;

    private record Captured(String authorization, String contentEncoding, String ttl, byte[] body, String path) {}

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();

        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        vapid = generator.generateKeyPair();
        receiver = generator.generateKeyPair();
        authSecret = new byte[16];
        new java.security.SecureRandom().nextBytes(authSecret);

        byte[] rawPrivate = new byte[32];
        byte[] s = ((ECPrivateKey) vapid.getPrivate()).getS().toByteArray();
        System.arraycopy(s, Math.max(0, s.length - 32), rawPrivate, 32 - Math.min(32, s.length), Math.min(32, s.length));
        gateway = new WebPushGateway(B64.encodeToString(EcKeys.encode((ECPublicKey) vapid.getPublic())), B64.encodeToString(rawPrivate),
                "mailto:test@khedma.local", Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC));
        target = new Target("http://127.0.0.1:" + server.getAddress().getPort() + "/push/device-1",
                B64.encodeToString(EcKeys.encode((ECPublicKey) receiver.getPublic())), B64.encodeToString(authSecret));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void deliversAnEncryptedSignedMessageTheDeviceCanOpen() throws Exception {
        String payload = "{\"title\":\"نقط جديدة\",\"body\":\"+٢ سلوك\",\"url\":\"/me\"}";

        assertThat(gateway.send(target, payload)).isEqualTo(Outcome.DELIVERED);

        Captured request = captured.get();
        assertThat(request.path()).isEqualTo("/push/device-1");
        assertThat(request.contentEncoding()).isEqualTo("aes128gcm");
        assertThat(request.ttl()).isEqualTo("86400");
        assertThat(decrypt(request.body())).isEqualTo(payload);
        assertVapidIsValid(request.authorization());
    }

    @Test
    void mapsPushServiceAnswers() {
        status = 410;
        assertThat(gateway.send(target, "{}")).isEqualTo(Outcome.GONE);
        status = 404;
        assertThat(gateway.send(target, "{}")).isEqualTo(Outcome.GONE);
        status = 500;
        assertThat(gateway.send(target, "{}")).isEqualTo(Outcome.FAILED);
        status = 429;
        assertThat(gateway.send(target, "{}")).isEqualTo(Outcome.FAILED);
    }

    @Test
    void anUnreachableServiceIsAFailureNotAnException() {
        server.stop(0);
        assertThat(gateway.send(target, "{}")).isEqualTo(Outcome.FAILED);
    }

    @Test
    void doesNotFollowRedirects() {
        status = 302;
        assertThat(gateway.send(target, "{}")).isEqualTo(Outcome.FAILED);
    }

    // ---- the browser's side ----

    private void handle(HttpExchange exchange) throws IOException {
        captured.set(new Captured(exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Content-Encoding"), exchange.getRequestHeaders().getFirst("TTL"),
                exchange.getRequestBody().readAllBytes(), exchange.getRequestURI().getPath()));
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    private void assertVapidIsValid(String authorization) throws Exception {
        String publicKey = B64.encodeToString(EcKeys.encode((ECPublicKey) vapid.getPublic()));
        assertThat(authorization).startsWith("vapid t=").endsWith(", k=" + publicKey);
        String jwt = authorization.substring("vapid t=".length(), authorization.indexOf(", k="));
        String[] parts = jwt.split("\\.");
        String claims = new String(UNB64.decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(claims).contains("\"aud\":\"http://127.0.0.1:" + server.getAddress().getPort() + "\"")
                .contains("\"sub\":\"mailto:test@khedma.local\"")
                .contains("\"exp\":" + Instant.parse("2026-10-14T22:00:00Z").getEpochSecond());
        Signature verify = Signature.getInstance("SHA256withECDSAinP1363Format");
        verify.initVerify(vapid.getPublic());
        verify.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verify.verify(UNB64.decode(parts[2]))).isTrue();
    }

    /** RFC 8291 / 8188 decryption with the receiver's private key, written separately from the sender's code. */
    private String decrypt(byte[] body) throws Exception {
        ByteBuffer in = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        in.get(salt);
        in.getInt(); // record size
        byte[] senderPublic = new byte[in.get() & 0xff];
        in.get(senderPublic);
        byte[] ciphertext = new byte[in.remaining()];
        in.get(ciphertext);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(receiver.getPrivate());
        agreement.doPhase(EcKeys.publicKey(senderPublic), true);
        byte[] ecdh = agreement.generateSecret();

        byte[] receiverPublic = EcKeys.encode((ECPublicKey) receiver.getPublic());
        byte[] keyInfo = ByteBuffer.allocate(14 + 65 + 65).put("WebPush: info\0".getBytes(StandardCharsets.US_ASCII))
                .put(receiverPublic).put(senderPublic).array();
        byte[] ikm = hmac(hmac(authSecret, ecdh), append(keyInfo, (byte) 1));
        byte[] prk = hmac(salt, ikm);
        byte[] key = Arrays.copyOf(hmac(prk, append("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), (byte) 1)), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, append("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), (byte) 1)), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);
        int end = padded.length - 1;
        while (padded[end] == 0) end--; // trailing zero padding
        assertThat(padded[end]).isEqualTo((byte) 2); // last-record delimiter
        return new String(padded, 0, end, StandardCharsets.UTF_8);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] append(byte[] data, byte last) {
        byte[] out = Arrays.copyOf(data, data.length + 1);
        out[data.length] = last;
        return out;
    }
}
