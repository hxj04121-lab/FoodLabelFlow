package com.spectrace.identity.infrastructure;

import com.spectrace.identity.application.AuthorizationService;
import com.spectrace.identity.application.IdentityService;
import com.spectrace.identity.application.port.AuthorizationPort;
import com.spectrace.identity.domain.AuthenticatedActor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** Resolves the authenticated request subject through the M4 identity/RBAC seam. */
@Component
public class RequestAuthorizationAdapter implements AuthorizationPort {

    public static final String AUTH_PROVIDER_HEADER = "X-Auth-Provider";
    public static final String AUTH_SUBJECT_HEADER = "X-External-Subject";

    private final HttpServletRequest request;
    private final IdentityService identityService;
    private final AuthorizationService authorizationService;

    public RequestAuthorizationAdapter(
            HttpServletRequest request,
            IdentityService identityService,
            AuthorizationService authorizationService
    ) {
        this.request = request;
        this.identityService = identityService;
        this.authorizationService = authorizationService;
    }

    @Override
    public AuthenticatedActor require(String permission) {
        String provider = request.getHeader(AUTH_PROVIDER_HEADER);
        String subject = request.getHeader(AUTH_SUBJECT_HEADER);
        if (provider == null || provider.isBlank() || subject == null || subject.isBlank()) {
            throw new com.spectrace.identity.application.UnknownIdentityException(
                    "Authenticated identity headers are required");
        }

        AuthenticatedActor actor = identityService.authenticate(provider, subject);
        authorizationService.requirePermission(actor, permission);
        return actor;
    }
}
