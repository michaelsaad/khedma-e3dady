package com.khedma;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.testcontainers.containers.PostgreSQLContainer;

import com.khedma.auth.otp.CodePurpose;
import com.khedma.auth.otp.EmailSender;
import com.khedma.config.AppProperties;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    /** Fixed "now": Saturday 10 Oct 2026, Cairo. Fridays 18 Sep .. 9 Oct are past meetings. */
    public static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 10, 12, 0, 0, 0, ZoneId.of("Africa/Cairo"));

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }

    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(NOW.toInstant(), NOW.getZone());
    }

    @Bean
    @Primary
    CapturingEmailSender capturingEmailSender(ObjectProvider<JavaMailSender> mailSender, AppProperties properties) {
        return new CapturingEmailSender(mailSender, properties);
    }

    /** Keeps the last code sent to each address instead of sending email. */
    public static class CapturingEmailSender extends EmailSender {

        private final Map<String, String> lastCodes = new ConcurrentHashMap<>();
        private final Map<String, CodePurpose> lastPurposes = new ConcurrentHashMap<>();

        CapturingEmailSender(ObjectProvider<JavaMailSender> mailSender, AppProperties properties) {
            super(mailSender, properties);
        }

        @Override
        public void sendCode(String to, String name, String code, Duration validFor, CodePurpose purpose) {
            lastCodes.put(to, code);
            lastPurposes.put(to, purpose);
        }

        public CodePurpose lastPurpose(String email) {
            return lastPurposes.get(email);
        }

        public String lastCode(String email) {
            return lastCodes.get(email);
        }
    }
}
