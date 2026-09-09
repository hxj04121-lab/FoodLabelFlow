package com.spectrace.identity.application.port;

import com.spectrace.identity.domain.AuthenticatedActor;

import java.util.Optional;

public interface IdentityRepository {

    Optional<AuthenticatedActor> findActiveActorByExternalSubject(
            String authProvider,
            String externalSubject
    );
}
