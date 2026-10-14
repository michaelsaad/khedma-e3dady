package com.khedma.push;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Web Push message encryption: RFC 8291 with the aes128gcm content coding of RFC 8188, done with the JDK only.
 * One record per message (payloads are tiny), so the result is {@code header || ciphertext}.
 */
final class WebPushCrypto {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RECORD_SIZE = 4096;

    private WebPushCrypto() {}

    /** Encrypts for one subscription with a fresh ephemeral key and salt. */
    static byte[] encrypt(byte[] plaintext, byte[] userAgentPublicKey, byte[] authSecret) throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(plaintext, userAgentPublicKey, authSecret, generator.generateKeyPair(), salt);
    }

    /** Deterministic variant so the RFC 8291 test vector can be checked. */
    static byte[] encrypt(byte[] plaintext, byte[] userAgentPublicKey, byte[] authSecret, KeyPair applicationServerKeys,
                          byte[] salt) throws GeneralSecurityException {
        if (plaintext.length > RECORD_SIZE - 18 - 1) {
            throw new GeneralSecurityException("Payload too large for one record");
        }
        ECPublicKey userAgentKey = EcKeys.publicKey(userAgentPublicKey);
        byte[] applicationServerPublicKey = EcKeys.encode((ECPublicKey) applicationServerKeys.getPublic());

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(applicationServerKeys.getPrivate());
        agreement.doPhase(userAgentKey, true);
        byte[] sharedSecret = agreement.generateSecret();

        // Combine the ECDH secret with the subscription's auth secret (RFC 8291 section 3.4).
        byte[] prkKey = hmac(authSecret, sharedSecret);
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), userAgentPublicKey, applicationServerPublicKey);
        byte[] ikm = hkdfExpand(prkKey, keyInfo, 32);

        // Content encryption key and nonce (RFC 8188 section 2.2 and 2.3).
        byte[] prk = hmac(salt, ikm);
        byte[] contentKey = hkdfExpand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdfExpand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(contentKey, "AES"), new GCMParameterSpec(128, nonce));
        // 0x02 marks the last (here: only) record.
        byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[] {2}));

        ByteBuffer out = ByteBuffer.allocate(16 + 4 + 1 + applicationServerPublicKey.length + ciphertext.length);
        out.put(salt).putInt(RECORD_SIZE).put((byte) applicationServerPublicKey.length).put(applicationServerPublicKey).put(ciphertext);
        return out.array();
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    /** HKDF-Expand for outputs of at most one hash block (32 bytes), which is all Web Push needs. */
    private static byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws GeneralSecurityException {
        return Arrays.copyOf(hmac(prk, concat(info, new byte[] {1})), length);
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] part : parts) total += part.length;
        byte[] out = new byte[total];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, offset, part.length);
            offset += part.length;
        }
        return out;
    }
}
