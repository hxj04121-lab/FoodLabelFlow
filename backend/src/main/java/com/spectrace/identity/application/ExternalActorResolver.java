package com.spectrace.identity.application;

import com.spectrace.identity.domain.AuthenticatedActor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Resolves the existing external identity contract and respects its development opt-out. */
@Component
public class ExternalActorResolver {
    private final IdentityService identities;
    private final boolean devExternalEnabled;

    public ExternalActorResolver(IdentityService identities,
            @Value("${spectrace.dev-external-auth.enabled:false}") boolean devExternalEnabled) {
        this.identities = identities;
        this.devExternalEnabled = devExternalEnabled;
    }

    public AuthenticatedActor resolve(String provider, String subject) {
        if (provider == null || provider.isBlank() || subject == null || subject.isBlank()) {
            throw new UnknownIdentityException("Authenticated identity headers are required");
        }
        // Provider namespaces are ASCII machine identifiers. MySQL's accent-insensitive
        // collation must not turn a different wire value into a disabled development identity.
        if (provider.chars().anyMatch(value -> value > 127)) {
            throw new UnknownIdentityException("Authentication provider namespace is not supported");
        }
        // Identity provider lookup is case-insensitive in MySQL; the flag must match that boundary.
        if ("DEV_EXTERNAL".equalsIgnoreCase(provider.trim()) && !devExternalEnabled) {
            throw new UnknownIdentityException("Development external authentication is disabled");
        }
        return identities.authenticate(provider, subject);
    }
}
