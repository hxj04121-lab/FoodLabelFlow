package com.spectrace.identity.application;

import com.spectrace.identity.domain.AuthenticatedActor;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {

    public void requirePermission(
            AuthenticatedActor actor,
            String permission
    ) {
        if (actor == null) {
            throw new AuthorizationDeniedException(
                    "Authenticated actor is required"
            );
        }

        if (permission == null || permission.isBlank()) {
            throw new IllegalArgumentException(
                    "Permission is required"
            );
        }

        if (!actor.hasPermission(permission)) {
            throw new AuthorizationDeniedException(
                    "Actor lacks required permission: " + permission
            );
        }
    }
}