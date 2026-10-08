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
        Cors cors) {

    public record Jwt(String secret, Duration ttl) {}

    public record Google(String clientId) {
        public boolean enabled() {
            return clientId != null && !clientId.isBlank();
        }
    }

    public record Otp(Duration ttl, Duration resendCooldown, int maxAttempts) {}

    public record Mail(String from) {}

    public record Cors(List<String> allowedOrigins) {}

    public Set<String> normalizedSuperAdminEmails() {
        if (superAdminEmails == null) return Set.of();
        return superAdminEmails.stream()
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
