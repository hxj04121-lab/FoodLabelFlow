package com.spectrace.identity.application.port;

import com.spectrace.identity.domain.AuthenticatedActor;

import java.util.List;
import java.util.Optional;

public interface IdentityRepository {

    Optional<AuthenticatedActor> findActiveActorByExternalSubject(
            String authProvider,
            String externalSubject
    );

    /** Active users whose roles grant the permission, ordered by userId. */
    List<String> findActiveUserIdsWithPermission(String permissionCode);
}
