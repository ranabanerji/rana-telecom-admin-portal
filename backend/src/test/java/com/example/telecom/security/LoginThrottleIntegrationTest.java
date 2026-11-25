package com.example.telecom.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login throttling through the real filter chain. Each test gets fresh
 * counters.
 */
@SpringBootTest(properties = {"app.auth.admin.username=throttle-admin",
        "app.auth.admin.password=throttle-admin-password", "app.auth.agent.username=throttle-agent",
        "app.auth.agent.password=throttle-agent-password", "app.auth.login-throttle.max-failures-per-user=3",
        "app.auth.login-throttle.max-failures-per-client=5", "app.auth.login-throttle.window=10m"})
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
class LoginThrottleIntegrationTest {

    private static final String ADMIN = "throttle-admin";
    private static final String ADMIN_PASSWORD = "throttle-admin-password";
    private static final String AGENT = "throttle-agent";
    private static final String AGENT_PASSWORD = "throttle-agent-password";
    private static final String CLIENT = "203.0.113.7";
    private static final String WRONG = "wrong-password";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void refusesEvenTheRightPasswordOnceTheUserIsOverTheLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            login(ADMIN, WRONG, CLIENT).andExpect(status().isUnauthorized());
        }

        login(ADMIN, ADMIN_PASSWORD, "198.51.100.2").andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "600"))
                .andExpect(jsonPath("$.error").value("Too many failed login attempts. Try again in 10 minutes."));
        login(AGENT, AGENT_PASSWORD, CLIENT).andExpect(status().isOk());
    }

    @Test
    void refusesAClientThatKeepsGuessingUsernames() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("guess-" + i, WRONG, CLIENT).andExpect(status().isUnauthorized());
        }

        login(AGENT, AGENT_PASSWORD, CLIENT).andExpect(status().isTooManyRequests());
        login(AGENT, AGENT_PASSWORD, "198.51.100.2").andExpect(status().isOk());
    }

    @Test
    void aSuccessfulLoginResetsTheUserCount() throws Exception {
        login(ADMIN, WRONG, CLIENT).andExpect(status().isUnauthorized());
        login(ADMIN, WRONG, CLIENT).andExpect(status().isUnauthorized());
        login(ADMIN, ADMIN_PASSWORD, CLIENT).andExpect(status().isOk());
        login(ADMIN, WRONG, CLIENT).andExpect(status().isUnauthorized());
        login(ADMIN, WRONG, CLIENT).andExpect(status().isUnauthorized());

        login(ADMIN, ADMIN_PASSWORD, CLIENT).andExpect(status().isOk());
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions login(String username, String password, String client) throws Exception {
        Cookie xsrf = new Cookie("XSRF-TOKEN", UUID.randomUUID().toString());
        return mockMvc.perform(MockMvcRequestBuilders.post(SecurityConfig.LOGIN_URL).cookie(xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue()).param("username", username).param("password", password)
                .with(request -> {
                    request.setRemoteAddr(client);
                    return request;
                }));
    }
}
