package com.example.telecom.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Demo login accounts. Values come from environment variables (see
 * application.yaml); an account without a password is not created.
 */
@ConfigurationProperties("app.auth")
public record AuthProperties(Account admin, Account agent) {

    public record Account(String username, String password, String name, String email) {

        boolean isConfigured() {
            return username != null && !username.isBlank() && password != null && !password.isBlank();
        }

        @Override
        public String toString() {
            return "Account[username=" + username + ", name=" + name + ", email=" + email + "]";
        }
    }
}
