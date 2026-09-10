package com.spectrace.identity.domain;

import java.util.Set;

public record AuthenticatedActor(
        String userId,
        String username,
        String displayName,
        Set<String> roles,
        Set<String> permissions
) {
    public AuthenticatedActor {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}