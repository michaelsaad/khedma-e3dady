package com.khedma.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.khedma.config.AppProperties;
import com.khedma.user.AppUser;

/** Remembers browsers that passed the emailed sign-in code. */
@Service
public class TrustedDeviceService {

    private final SecureRandom random = new SecureRandom();
    private final TrustedDeviceRepository devices;
    private final AppProperties.TwoFactor settings;
    private final Clock clock;

    public TrustedDeviceService(TrustedDeviceRepository devices, AppProperties properties, Clock clock) {
        this.devices = devices;
        this.settings = properties.twoFactor();
        this.clock = clock;
    }

    /** Returns the raw token for the browser to keep; only its hash is stored. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String trust(AppUser user, String userAgent) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        TrustedDevice device = new TrustedDevice();
        device.setUserId(user.getId());
        device.setTokenHash(hash(token));
        device.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 255)));
        device.setCreatedAt(now);
        device.setLastUsedAt(now);
        device.setExpiresAt(now.plus(settings.trustedDeviceTtl()));
        devices.save(device);
        return token;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean isTrusted(AppUser user, String token) {
        if (token == null || token.isBlank() || token.length() > 100) return false;
        Instant now = clock.instant();
        return devices.findByTokenHash(hash(token))
                .filter(device -> device.getUserId().equals(user.getId()))
                .filter(device -> device.getExpiresAt().isAfter(now))
                .map(device -> {
                    device.setLastUsedAt(now);
                    return true;
                })
                .orElse(false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void forgetAll(Long userId) {
        devices.deleteAllByUserId(userId);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
