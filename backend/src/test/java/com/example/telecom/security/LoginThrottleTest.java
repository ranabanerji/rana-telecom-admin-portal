package com.example.telecom.security;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

class LoginThrottleTest {

    private static final String ADMIN = "admin";
    private static final String CLIENT = "203.0.113.7";
    private static final String OTHER_CLIENT = "198.51.100.2";
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-25T10:00:00Z"));

    @Test
    void blocksAKnownUserAfterTheLimitUntilTheWindowEnds() {
        LoginThrottle throttle = throttle(3, 100, "");
        for (int i = 0; i < 3; i++) {
            Assertions.assertEquals(Duration.ZERO, throttle.retryAfter(ADMIN, CLIENT), "attempt " + i);
            throttle.recordFailure(ADMIN, CLIENT);
        }
        clock.advance(Duration.ofMinutes(5));

        Assertions.assertEquals(Duration.ofMinutes(10), throttle.retryAfter(ADMIN, OTHER_CLIENT),
                "a user over the limit is blocked from every client");

        clock.advance(Duration.ofMinutes(10));
        Assertions.assertEquals(Duration.ZERO, throttle.retryAfter(ADMIN, OTHER_CLIENT),
                "the block ends with the window");
    }

    @Test
    void unknownUsernamesCountOnlyAgainstTheClient() {
        LoginThrottle throttle = throttle(2, 3, "");
        for (int i = 0; i < 3; i++) {
            throttle.recordFailure("guess-" + i, CLIENT);
        }

        Assertions.assertEquals(WINDOW, throttle.retryAfter(ADMIN, CLIENT), "client over its limit is blocked");
        Assertions.assertEquals(Duration.ZERO, throttle.retryAfter(ADMIN, OTHER_CLIENT),
                "unknown usernames must not block the real account elsewhere");
    }

    @Test
    void successClearsTheUserButNotTheClient() {
        LoginThrottle throttle = throttle(2, 2, "");
        throttle.recordFailure(ADMIN, CLIENT);
        throttle.recordFailure(ADMIN, CLIENT);
        throttle.recordSuccess(ADMIN);

        Assertions.assertEquals(Duration.ZERO, throttle.retryAfter(ADMIN, OTHER_CLIENT), "user count reset");
        Assertions.assertEquals(WINDOW, throttle.retryAfter(ADMIN, CLIENT), "client count kept");
    }

    @Test
    void usesTheConfiguredClientHeader() {
        LoginThrottle throttle = throttle(5, 5, "CF-Connecting-IP");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("CF-Connecting-IP", CLIENT + ", 10.0.0.9");

        Assertions.assertEquals(CLIENT, throttle.clientOf(request), "first address in the header");
    }

    @Test
    void ignoresClientHeadersUnlessConfigured() {
        LoginThrottle throttle = throttle(5, 5, "");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("CF-Connecting-IP", CLIENT);
        request.addHeader("X-Forwarded-For", CLIENT);

        Assertions.assertEquals("10.0.0.1", throttle.clientOf(request), "client-supplied headers are not trusted");
    }

    private LoginThrottle throttle(int perUser, int perClient, String header) {
        AuthProperties.Account admin = new AuthProperties.Account(ADMIN, "admin-password-123", ADMIN, "");
        DemoUserDirectory directory = new DemoUserDirectory(new AuthProperties(admin, null),
                PasswordEncoderFactories.createDelegatingPasswordEncoder());
        return new LoginThrottle(new LoginThrottleProperties(perUser, perClient, WINDOW, header), directory, clock);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            super();
            this.now = start;
        }

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
}
