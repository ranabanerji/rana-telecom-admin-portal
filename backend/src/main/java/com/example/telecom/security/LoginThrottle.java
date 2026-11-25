package com.example.telecom.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts failed logins per demo username and per client address in fixed
 * windows.
 *
 * <p>
 * Only configured usernames are tracked, so guessing random names cannot grow
 * the user map; those attempts still count against the client.
 */
@Component
public class LoginThrottle {

    // Stops an attacker with many addresses from growing the map without bound.
    // Past this size new clients are not tracked, but the per-user limit still
    // applies.
    private static final int MAX_TRACKED_CLIENTS = 10_000;
    private static final String UNKNOWN_CLIENT = "unknown";

    private final LoginThrottleProperties properties;
    private final DemoUserDirectory directory;
    private final Clock clock;
    private final Map<String, Window> users = new ConcurrentHashMap<>();
    private final Map<String, Window> clients = new ConcurrentHashMap<>();

    @Autowired
    public LoginThrottle(LoginThrottleProperties properties, DemoUserDirectory directory) {
        this(properties, directory, Clock.systemUTC());
    }

    LoginThrottle(LoginThrottleProperties properties, DemoUserDirectory directory, Clock clock) {
        this.properties = properties;
        this.directory = directory;
        this.clock = clock;
    }

    /** The address failures are counted against for this request. */
    public String clientOf(HttpServletRequest request) {
        String header = properties.clientIpHeader();
        if (header != null && !header.isBlank()) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                int comma = value.indexOf(',');
                return (comma < 0 ? value : value.substring(0, comma)).trim();
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null ? UNKNOWN_CLIENT : remote;
    }

    /**
     * How long until {@code username} and {@code client} may try again, or
     * {@link Duration#ZERO} when neither is blocked.
     */
    public Duration retryAfter(String username, String client) {
        Instant now = clock.instant();
        Duration byUser = isKnown(username)
                ? blockedFor(users.get(username), properties.maxFailuresPerUser(), now)
                : Duration.ZERO;
        Duration byClient = blockedFor(clients.get(key(client)), properties.maxFailuresPerClient(), now);
        return byUser.compareTo(byClient) >= 0 ? byUser : byClient;
    }

    public void recordFailure(String username, String client) {
        Instant now = clock.instant();
        if (isKnown(username)) {
            users.compute(username, (name, window) -> next(window, now));
        }
        String clientKey = key(client);
        if (clients.size() >= MAX_TRACKED_CLIENTS) {
            clients.values().removeIf(window -> window.hasEnded(now, properties.window()));
        }
        if (clients.size() < MAX_TRACKED_CLIENTS || clients.containsKey(clientKey)) {
            clients.compute(clientKey, (address, window) -> next(window, now));
        }
    }

    /**
     * Clears the user's failures. The client's count stays, so knowing one password
     * does not reset the limit for guessing others.
     */
    public void recordSuccess(String username) {
        if (username != null) {
            users.remove(username);
        }
    }

    private boolean isKnown(String username) {
        return username != null && directory.findProfile(username).isPresent();
    }

    private static String key(String client) {
        return client == null ? UNKNOWN_CLIENT : client;
    }

    private Window next(Window current, Instant now) {
        if (current == null || current.hasEnded(now, properties.window())) {
            return new Window(now, 1);
        }
        return new Window(current.start(), current.failures() + 1);
    }

    private Duration blockedFor(Window window, int maxFailures, Instant now) {
        if (window == null || window.failures() < maxFailures || window.hasEnded(now, properties.window())) {
            return Duration.ZERO;
        }
        return Duration.between(now, window.start().plus(properties.window()));
    }

    private record Window(Instant start, int failures) {
        boolean hasEnded(Instant now, Duration length) {
            return !now.isBefore(start.plus(length));
        }
    }
}
