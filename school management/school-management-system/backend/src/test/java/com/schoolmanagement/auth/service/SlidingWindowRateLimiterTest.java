package com.schoolmanagement.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SlidingWindowRateLimiterTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-19T06:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final SlidingWindowRateLimiter limiter =
            new SlidingWindowRateLimiter(3, Duration.ofMinutes(10), clock);

    @Test
    void blocksOnlyOnceTheLimitIsReached() {
        limiter.record("k");
        limiter.record("k");
        assertThat(limiter.isBlocked("k")).isFalse();

        limiter.record("k");

        assertThat(limiter.isBlocked("k")).isTrue();
        assertThat(limiter.retryAfter("k")).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void unblocksWhenOldEventsLeaveTheWindow() {
        limiter.record("k");
        clock.advance(Duration.ofMinutes(4));
        limiter.record("k");
        limiter.record("k");
        assertThat(limiter.retryAfter("k")).isEqualTo(Duration.ofMinutes(6));

        clock.advance(Duration.ofMinutes(6));

        assertThat(limiter.isBlocked("k")).isFalse();
    }

    @Test
    void keysAreIndependentAndResettable() {
        for (int i = 0; i < 3; i++) {
            limiter.record("a");
        }

        assertThat(limiter.isBlocked("a")).isTrue();
        assertThat(limiter.isBlocked("b")).isFalse();

        limiter.reset("a");
        assertThat(limiter.isBlocked("a")).isFalse();
    }

    @Test
    void purgeRemovesIdleKeysSoMemoryIsReclaimed() {
        limiter.record("a");
        clock.advance(Duration.ofMinutes(11));

        limiter.purge();

        assertThat(limiter.retryAfter("a")).isEqualTo(Duration.ZERO);
    }
}
