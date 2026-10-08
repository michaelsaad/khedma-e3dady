package com.khedma;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;

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
}
