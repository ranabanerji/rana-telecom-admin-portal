package com.example.telecom.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Limits on failed logins. A known username or a client that reaches its limit
 * cannot try again until the window that began with its first failure ends.
 *
 * @param clientIpHeader request header that carries the real client address,
 *            for example {@code CF-Connecting-IP}. Leave it empty unless every
 *            request reaches the backend through a proxy that sets the header,
 *            because clients can send any header they like.
 */
@ConfigurationProperties("app.auth.login-throttle")
public record LoginThrottleProperties(@DefaultValue("5") int maxFailuresPerUser,
        @DefaultValue("50") int maxFailuresPerClient, @DefaultValue("15m") Duration window,
        @DefaultValue("") String clientIpHeader) {
}
