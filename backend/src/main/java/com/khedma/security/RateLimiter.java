package com.khedma.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Small in-memory fixed-window counter. The API runs as a single instance, so memory is enough;
 * old windows are dropped as the map grows.
 */
@Component
public class RateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Window(Instant start, int count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Counts one hit for {@code key}; false when the key is already over {@code limit} in this window. */
    public boolean tryAcquire(String key, int limit, Duration window) {
        Instant now = clock.instant();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> !w.start().plus(window).isAfter(now));
        }
        Window updated = windows.compute(key, (k, current) ->
                current == null || !current.start().plus(window).isAfter(now)
                        ? new Window(now, 1)
                        : new Window(current.start(), current.count() + 1));
        return updated.count() <= limit;
    }

    /** True while {@code key} has reached {@code limit} in the current window (doesn't count a hit). */
    public boolean isBlocked(String key, int limit, Duration window) {
        Window current = windows.get(key);
        return current != null && current.start().plus(window).isAfter(clock.instant()) && current.count() >= limit;
    }

    public void reset(String key) {
        windows.remove(key);
    }
}
