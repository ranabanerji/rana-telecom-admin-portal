package com.example.telecom.controller;

import com.example.telecom.security.DemoUserDirectory;
import com.example.telecom.security.UserProfile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session helpers for the frontend. Login and logout are handled by Spring
 * Security at {@code /api/auth/login} and {@code /api/auth/logout}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final DemoUserDirectory directory;

    public AuthController(DemoUserDirectory directory) {
        this.directory = directory;
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfile> currentUser(Authentication authentication) {
        return directory.findProfile(authentication.getName()).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    /**
     * Issues the {@code XSRF-TOKEN} cookie. Clients call this before their first
     * unsafe request (such as login).
     */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        // The token is deferred; reading it is what makes the repository write the
        // cookie.
        token.getToken();
        return ResponseEntity.noContent().build();
    }
}
