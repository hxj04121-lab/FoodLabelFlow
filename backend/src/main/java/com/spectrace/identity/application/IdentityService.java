package com.spectrace.identity.application;

import com.spectrace.identity.application.port.IdentityRepository;
import com.spectrace.identity.domain.AuthenticatedActor;
import org.springframework.stereotype.Service;

@Service
public class IdentityService {

    private final IdentityRepository identityRepository;

    public IdentityService(IdentityRepository identityRepository) {
        this.identityRepository = identityRepository;
    }

    public AuthenticatedActor authenticate(
            String authProvider,
            String externalSubject
    ) {
        if (authProvider == null || authProvider.isBlank()
                || externalSubject == null || externalSubject.isBlank()) {
            throw new IllegalArgumentException(
                    "Authentication provider and external subject are required"
            );
        }

        return identityRepository
                .findActiveActorByExternalSubject(authProvider, externalSubject)
                .orElseThrow(() ->
                        new UnknownIdentityException(
                                "Authenticated identity is not mapped to an active SpecTrace user"
                        )
                );
    }
}