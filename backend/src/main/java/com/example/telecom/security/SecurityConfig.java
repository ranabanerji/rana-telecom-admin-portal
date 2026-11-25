package com.example.telecom.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Session-cookie authentication for the API.
 *
 * <p>
 * The frontend logs in with a form POST to {@code /api/auth/login}, which
 * creates a session cookie. Unsafe requests must echo the {@code XSRF-TOKEN}
 * cookie in an {@code X-XSRF-TOKEN} header.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({AuthProperties.class, LoginThrottleProperties.class})
public class SecurityConfig {

    public static final String LOGIN_URL = "/api/auth/login";
    public static final String LOGOUT_URL = "/api/auth/logout";

    private static final String ADMIN = Role.ADMIN.name();
    private static final String AGENT = Role.AGENT.name();

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, DemoUserDirectory directory,
            JsonMapper jsonMapper, LoginThrottle throttle,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookies) {
        HttpStatusEntryPoint unauthorized = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
        http.authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.GET, "/api/health", "/api/auth/csrf")
                .permitAll().requestMatchers("/error", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                .permitAll().requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole(ADMIN, AGENT)
                .requestMatchers(HttpMethod.POST, "/api/tickets").hasAnyRole(ADMIN, AGENT)
                .requestMatchers(HttpMethod.PUT, "/api/tickets/*").hasAnyRole(ADMIN, AGENT).requestMatchers("/api/**")
                .hasRole(ADMIN).anyRequest().denyAll())
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository(secureCookies)))
                .addFilterBefore(new LoginThrottleFilter(throttle, jsonMapper),
                        UsernamePasswordAuthenticationFilter.class)
                .formLogin(form -> form.loginPage("/login").loginProcessingUrl(LOGIN_URL)
                        .successHandler(loginSuccessHandler(directory, throttle, jsonMapper))
                        .failureHandler(loginFailureHandler(throttle, jsonMapper)).permitAll())
                .logout(logout -> logout.logoutUrl(LOGOUT_URL)
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .permitAll())
                .exceptionHandling(
                        ex -> ex.authenticationEntryPoint(unauthorized).accessDeniedHandler(accessDeniedHandler()))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * The XSRF-TOKEN cookie follows the session cookie's {@code Secure} setting,
     * because behind a TLS-terminating proxy the request itself looks like plain
     * HTTP.
     */
    private static CookieCsrfTokenRepository csrfTokenRepository(boolean secureCookies) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> {
            cookie.sameSite("Lax");
            if (secureCookies) {
                cookie.secure(true);
            }
        });
        return repository;
    }

    private static AuthenticationSuccessHandler loginSuccessHandler(DemoUserDirectory directory, LoginThrottle throttle,
            JsonMapper jsonMapper) {
        return (request, response, authentication) -> {
            throttle.recordSuccess(authentication.getName());
            UserProfile profile = directory.findProfile(authentication.getName()).orElseThrow();
            response.setStatus(HttpStatus.OK.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            jsonMapper.writeValue(response.getWriter(), profile);
        };
    }

    private static AuthenticationFailureHandler loginFailureHandler(LoginThrottle throttle, JsonMapper jsonMapper) {
        return (request, response, exception) -> {
            throttle.recordFailure(request.getParameter("username"), throttle.clientOf(request));
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            jsonMapper.writeValue(response.getWriter(), Map.of("error", "Invalid username or password"));
        };
    }

    /**
     * Requests rejected before authorization (for example a POST without a CSRF
     * token) are reported as 401 when nobody is logged in, so clients see one
     * consistent "log in first" status.
     */
    private static AccessDeniedHandler accessDeniedHandler() {
        AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();
        return (request, response, exception) -> {
            Authentication authentication = SecurityContextHolder.getContextHolderStrategy().getContext()
                    .getAuthentication();
            boolean loggedIn = authentication != null && !trustResolver.isAnonymous(authentication);
            response.sendError(loggedIn ? HttpStatus.FORBIDDEN.value() : HttpStatus.UNAUTHORIZED.value());
        };
    }
}
