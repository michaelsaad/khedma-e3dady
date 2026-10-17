package com.khedma.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khedma.config.AppProperties;

class RateLimiterTest {

    /** A clock the test can move forward. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-10T10:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MovableClock clock = new MovableClock();
    private final RateLimiter limiter = new RateLimiter(clock);

    @Test
    void allowsUpToTheLimitPerWindowThenStartsOver() {
        Duration window = Duration.ofMinutes(10);
        for (int i = 0; i < 3; i++) assertThat(limiter.tryAcquire("k", 3, window)).isTrue();
        assertThat(limiter.tryAcquire("k", 3, window)).isFalse();
        assertThat(limiter.isBlocked("k", 3, window)).isTrue();
        assertThat(limiter.tryAcquire("other", 3, window)).isTrue();

        clock.advance(window);
        assertThat(limiter.isBlocked("k", 3, window)).isFalse();
        assertThat(limiter.tryAcquire("k", 3, window)).isTrue();
    }

    @Test
    void filterAnswers429OnceAnIpUsedItsBudget() throws Exception {
        AppProperties properties = new AppProperties(null, null, null, null, null, List.of(), null, null,
                new AppProperties.RateLimit(true, 2, Duration.ofMinutes(10), 8, Duration.ofMinutes(15)), null);
        AuthRateLimitFilter filter = new AuthRateLimitFilter(limiter, properties, new ObjectMapper());

        assertThat(hit(filter, "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(hit(filter, "1.1.1.1").getStatus()).isEqualTo(200);
        MockHttpServletResponse blocked = hit(filter, "1.1.1.1");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("RATE_LIMITED");
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("600");
        // Another client is not affected.
        assertThat(hit(filter, "2.2.2.2").getStatus()).isEqualTo(200);
    }

    private static MockHttpServletResponse hit(AuthRateLimitFilter filter, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader("CF-Connecting-IP", ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
