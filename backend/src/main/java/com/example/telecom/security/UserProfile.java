package com.example.telecom.security;

/**
 * The signed-in user as exposed to the frontend.
 */
public record UserProfile(String username, String name, String email, Role role) {
}
