package com.khedma.push;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/** P-256 key conversions for Web Push: raw 32-byte private keys and 65-byte uncompressed public points. */
final class EcKeys {

    static final int PUBLIC_KEY_LENGTH = 65;
    private static final Base64.Decoder B64_URL = Base64.getUrlDecoder();

    private EcKeys() {}

    static ECParameterSpec p256() throws GeneralSecurityException {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    /** Decodes the uncompressed point (0x04 || X || Y) browsers give as {@code p256dh} and VAPID public keys use. */
    static ECPublicKey publicKey(byte[] encoded) throws GeneralSecurityException {
        if (encoded.length != PUBLIC_KEY_LENGTH || encoded[0] != 0x04) {
            throw new GeneralSecurityException("Not an uncompressed P-256 public key");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(encoded, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(encoded, 33, 65));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), p256()));
    }

    static ECPrivateKey privateKey(byte[] raw) throws GeneralSecurityException {
        return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, raw), p256()));
    }

    static byte[] encode(ECPublicKey key) {
        byte[] out = new byte[PUBLIC_KEY_LENGTH];
        out[0] = 0x04;
        System.arraycopy(fixed32(key.getW().getAffineX()), 0, out, 1, 32);
        System.arraycopy(fixed32(key.getW().getAffineY()), 0, out, 33, 32);
        return out;
    }

    static byte[] decodeBase64Url(String value) {
        return B64_URL.decode(value.trim());
    }

    private static byte[] fixed32(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length == 32) return bytes;
        byte[] out = new byte[32];
        if (bytes.length > 32) System.arraycopy(bytes, bytes.length - 32, out, 0, 32); // leading sign byte
        else System.arraycopy(bytes, 0, out, 32 - bytes.length, bytes.length);
        return out;
    }
}
