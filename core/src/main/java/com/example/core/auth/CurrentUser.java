package com.example.core.auth;

import java.util.Set;

public record CurrentUser(String id, String displayName, Set<String> roles) {

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
