package com.spectrace.identity.application.port;

import com.spectrace.identity.domain.AuthenticatedActor;

/** Application-level authorization contract for modules that need a request actor. */
public interface AuthorizationPort {

    AuthenticatedActor require(String permission);
}
