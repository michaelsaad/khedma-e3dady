package com.khedma.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        ZoneId timezone,
        Jwt jwt,
        Google google,
        Otp otp,
        Mail mail,
        List<String> superAdminEmails,
        Cors cors,
        Push push,
        RateLimit rateLimit,
        TwoFactor twoFactor) {

    public record Jwt(String secret, Duration ttl) {}

    public record Google(String clientId) {
        public boolean enabled() {
            return clientId != null && !clientId.isBlank();
        }
    }

    public record Otp(Duration ttl, Duration resendCooldown, int maxAttempts, int maxSendsPerDay, int maxFailuresPerHour) {}

    /** Per-client-IP request budget for the public /api/auth endpoints, and failed sign-ins per email. */
    public record RateLimit(boolean enabled, int authRequestsPerIp, Duration authWindow,
                            int loginFailuresPerEmail, Duration loginWindow) {}

    /** Email/password sign-ins from a browser that is not trusted yet must confirm an emailed code. */
    public record TwoFactor(boolean enabled, Duration challengeTtl, Duration trustedDeviceTtl) {}

    /** {@code appUrl} is where the one-tap links in emails point; defaults to the first CORS origin. */
    public record Mail(String from, String brevoApiKey, String brevoBaseUrl, String appUrl) {
        public boolean brevoEnabled() {
            return brevoApiKey != null && !brevoApiKey.isBlank();
        }
    }

    public record Cors(List<String> allowedOrigins) {}

    /**
     * Web Push. The VAPID keys identify this server to browsers' push services; without them push is off.
     * Cron expressions run in {@link #timezone()}.
     */
    public record Push(String publicKey, String privateKey, String subject, boolean schedulerEnabled,
                       String fridayReminderCron, String absenceAlertCron, int absenceMissedMeetings) {
        public boolean enabled() {
            return notBlank(publicKey) && notBlank(privateKey) && notBlank(subject);
        }

        private static boolean notBlank(String value) {
            return value != null && !value.isBlank();
        }
    }

    /** Public address of the web app, without a trailing slash. */
    public String appUrl() {
        String url = mail.appUrl();
        if ((url == null || url.isBlank()) && cors != null && cors.allowedOrigins() != null && !cors.allowedOrigins().isEmpty()) {
            url = cors.allowedOrigins().get(0);
        }
        if (url == null || url.isBlank()) url = "http://localhost:3000";
        url = url.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public Set<String> normalizedSuperAdminEmails() {
        if (superAdminEmails == null) return Set.of();
        return superAdminEmails.stream()
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
