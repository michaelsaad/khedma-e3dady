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
        Push push) {

    public record Jwt(String secret, Duration ttl) {}

    public record Google(String clientId) {
        public boolean enabled() {
            return clientId != null && !clientId.isBlank();
        }
    }

    public record Otp(Duration ttl, Duration resendCooldown, int maxAttempts) {}

    public record Mail(String from, String brevoApiKey, String brevoBaseUrl) {
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

    public Set<String> normalizedSuperAdminEmails() {
        if (superAdminEmails == null) return Set.of();
        return superAdminEmails.stream()
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
