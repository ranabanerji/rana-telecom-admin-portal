package com.example.telecom.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Holds the seeded demo accounts in memory. Passwords are hashed at startup and
 * never stored in plain text.
 */
@Component
public final class DemoUserDirectory implements UserDetailsService {

    private static final Logger LOG = LoggerFactory.getLogger(DemoUserDirectory.class);

    private final Map<String, Entry> users = new HashMap<>();

    public DemoUserDirectory(AuthProperties properties, PasswordEncoder passwordEncoder) {
        register(properties.admin(), Role.ADMIN, "DEMO_ADMIN_PASSWORD", passwordEncoder);
        register(properties.agent(), Role.AGENT, "DEMO_AGENT_PASSWORD", passwordEncoder);
        if (users.isEmpty()) {
            LOG.warn("No demo accounts are configured, so nobody can log in. "
                    + "Set DEMO_ADMIN_PASSWORD and DEMO_AGENT_PASSWORD (see README).");
        }
    }

    private void register(AuthProperties.Account account, Role role, String passwordVariable,
            PasswordEncoder passwordEncoder) {
        if (account == null || !account.isConfigured()) {
            LOG.warn("Demo {} account is disabled because {} is not set", role, passwordVariable);
            return;
        }
        String username = account.username().trim();
        if (users.containsKey(username)) {
            throw new IllegalStateException("Demo accounts must have distinct usernames: " + username);
        }
        UserDetails details = User.withUsername(username).password(passwordEncoder.encode(account.password()))
                .roles(role.name()).build();
        String name = account.name() == null || account.name().isBlank() ? username : account.name();
        String email = account.email() == null ? "" : account.email();
        users.put(username, new Entry(details, new UserProfile(username, name, email, role)));
        LOG.info("Demo {} account enabled: username={}", role, username);
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        Entry entry = users.get(username);
        if (entry == null) {
            throw new UsernameNotFoundException("Unknown user");
        }
        // Spring Security erases credentials after login, so hand out a copy.
        return User.withUserDetails(entry.details()).build();
    }

    public Optional<UserProfile> findProfile(String username) {
        return Optional.ofNullable(users.get(username)).map(Entry::profile);
    }

    private record Entry(UserDetails details, UserProfile profile) {
    }
}
