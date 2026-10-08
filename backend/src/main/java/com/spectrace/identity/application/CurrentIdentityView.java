package com.spectrace.identity.application;

import com.spectrace.identity.domain.AuthenticatedActor;
import java.util.List;

/** Current mapped actor only; no external subject, email, password or identity selection. */
public record CurrentIdentityView(String userId, String username, String displayName,
                                  List<String> roles, List<String> permissions) {
    public CurrentIdentityView {
        roles = List.copyOf(roles);
        permissions = List.copyOf(permissions);
    }

    public static CurrentIdentityView from(AuthenticatedActor actor) {
        return new CurrentIdentityView(actor.userId(), actor.username(), actor.displayName(),
                actor.roles().stream().sorted().toList(), actor.permissions().stream().sorted().toList());
    }
}
