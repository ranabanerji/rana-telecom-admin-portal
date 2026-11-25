package com.example.telecom.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Refuses login attempts with 429 while the username or the client is over its
 * failure limit. Runs before authentication, so a blocked attempt never checks
 * the password.
 */
final class LoginThrottleFilter extends OncePerRequestFilter {

    private static final long SECONDS_PER_MINUTE = 60;

    private final RequestMatcher loginRequest = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST,
            SecurityConfig.LOGIN_URL);
    private final LoginThrottle throttle;
    private final JsonMapper jsonMapper;

    LoginThrottleFilter(LoginThrottle throttle, JsonMapper jsonMapper) {
        super();
        this.throttle = throttle;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (loginRequest.matches(request)) {
            Duration wait = throttle.retryAfter(request.getParameter("username"), throttle.clientOf(request));
            if (!wait.isZero() && !wait.isNegative()) {
                reject(response, wait);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, Duration wait) throws IOException {
        long seconds = Math.max(1, wait.plusMillis(999).toSeconds());
        long minutes = Math.max(1, (seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String delay = minutes == 1 ? "1 minute" : minutes + " minutes";
        jsonMapper.writeValue(response.getWriter(),
                Map.of("error", "Too many failed login attempts. Try again in " + delay + "."));
    }
}
