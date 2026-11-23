package com.schoolmanagement.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window event counter. It protects a single application instance; a multi-instance
 * deployment needs a shared store (for example Redis) behind the same API.
 */
public class SlidingWindowRateLimiter {

    private final int maxEvents;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Instant>> events = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(int maxEvents, Duration window, Clock clock) {
        this.maxEvents = maxEvents;
        this.window = window;
        this.clock = clock;
    }

    public boolean isBlocked(String key) {
        return retryAfter(key).compareTo(Duration.ZERO) > 0;
    }

    /** Time until the key is no longer blocked, or zero when it is not blocked. */
    public Duration retryAfter(String key) {
        Deque<Instant> deque = events.get(key);
        if (deque == null) {
            return Duration.ZERO;
        }
        synchronized (deque) {
            Instant now = clock.instant();
            prune(deque, now);
            if (deque.size() < maxEvents) {
                return Duration.ZERO;
            }
            return Duration.between(now, deque.peekFirst().plus(window));
        }
    }

    public void record(String key) {
        Deque<Instant> deque = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            Instant now = clock.instant();
            prune(deque, now);
            deque.addLast(now);
        }
    }

    public void reset(String key) {
        events.remove(key);
    }

    /** Drops idle keys so the map cannot grow without bound. */
    public void purge() {
        Instant now = clock.instant();
        events.entrySet().removeIf(entry -> {
            Deque<Instant> deque = entry.getValue();
            synchronized (deque) {
                prune(deque, now);
                return deque.isEmpty();
            }
        });
    }

    private void prune(Deque<Instant> deque, Instant now) {
        Instant cutoff = now.minus(window);
        while (!deque.isEmpty() && !deque.peekFirst().isAfter(cutoff)) {
            deque.removeFirst();
        }
    }
}
