package com.example.telecom.security;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

class DemoUserDirectoryTest {

    private static final String ADMIN = "admin";
    private static final String AGENT = "agent";
    private static final String SECRET = "s3cret-for-unit-test";

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Test
    void storesOnlyAHashOfThePassword() {
        UserDetails admin = directory(SECRET, "another-secret").loadUserByUsername(ADMIN);

        Assertions.assertTrue(!SECRET.equals(admin.getPassword()) && encoder.matches(SECRET, admin.getPassword()),
                "password should be stored as a hash that matches the configured value");
    }

    @Test
    void grantsTheConfiguredRole() {
        UserDetails admin = directory(SECRET, "another-secret").loadUserByUsername(ADMIN);

        Assertions.assertEquals(List.of("ROLE_ADMIN"),
                admin.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList(),
                "admin account should carry ROLE_ADMIN only");
    }

    @Test
    void exposesTheProfileForTheFrontend() {
        UserProfile agent = directory(SECRET, "another-secret").findProfile(AGENT).orElseThrow();

        Assertions.assertEquals(new UserProfile(AGENT, AGENT + " name", AGENT + "@example.com", Role.AGENT), agent,
                "profile should echo the configured name, email and role");
    }

    @Test
    void skipsAccountsWithoutAPassword() {
        DemoUserDirectory directory = directory(SECRET, "");

        Assertions.assertThrows(UsernameNotFoundException.class, () -> directory.loadUserByUsername(AGENT),
                "an account without a password must not be able to log in");
    }

    @Test
    void startsWithNoAccountsWhenNothingIsConfigured() {
        DemoUserDirectory directory = new DemoUserDirectory(new AuthProperties(null, null), encoder);

        Assertions.assertThrows(UsernameNotFoundException.class, () -> directory.loadUserByUsername(ADMIN),
                "no account should exist without configuration");
    }

    @Test
    void rejectsDuplicateUsernames() {
        AuthProperties properties = new AuthProperties(account(ADMIN, SECRET), account(ADMIN, SECRET));

        Assertions.assertThrows(IllegalStateException.class, () -> new DemoUserDirectory(properties, encoder),
                "two accounts with one username should fail fast");
    }

    @Test
    void refusesPasswordsShorterThanTheMinimum() {
        AuthProperties properties = new AuthProperties(account(ADMIN, "eleven-char"), null);

        IllegalStateException error = Assertions.assertThrows(IllegalStateException.class,
                () -> new DemoUserDirectory(properties, encoder), "a short password should stop startup");
        Assertions.assertTrue(error.getMessage().contains("DEMO_ADMIN_PASSWORD"), error.getMessage());
    }

    @Test
    void accountToStringHidesThePassword() {
        Assertions.assertFalse(account(ADMIN, SECRET).toString().contains(SECRET),
                "toString must not leak the password into logs");
    }

    private DemoUserDirectory directory(String adminPassword, String agentPassword) {
        return new DemoUserDirectory(new AuthProperties(account(ADMIN, adminPassword), account(AGENT, agentPassword)),
                encoder);
    }

    private static AuthProperties.Account account(String username, String password) {
        return new AuthProperties.Account(username, password, username + " name", username + "@example.com");
    }
}
