package com.spectrace.catalog.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.spectrace.audit.application.AuditApplicationService;
import com.spectrace.catalog.application.CatalogIntegration;
import com.spectrace.catalog.domain.CatalogFailure;
import com.spectrace.identity.application.AuthorizationDeniedException;
import com.spectrace.identity.application.AuthorizationService;
import com.spectrace.identity.application.IdentityService;
import com.spectrace.identity.application.UnknownIdentityException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** Connects catalog writes to the M4 identity seam and transactional audit log. */
@Component
@ConditionalOnProperty(
        name = "spectrace.dev-external-auth.enabled",
        havingValue = "true"
)
public class RequestCatalogIntegration implements CatalogIntegration {    public static final String AUTH_PROVIDER_HEADER = "X-Auth-Provider";
    public static final String AUTH_SUBJECT_HEADER = "X-External-Subject";

    private final HttpServletRequest request;
    private final IdentityService identityService;
    private final AuthorizationService authorizationService;
    private final AuditApplicationService auditService;

    public RequestCatalogIntegration(
            HttpServletRequest request,
            IdentityService identityService,
            AuthorizationService authorizationService,
            AuditApplicationService auditService
    ) {
        this.request = request;
        this.identityService = identityService;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    @Override
    public String requireActor(String permission) {
        String provider = request.getHeader(AUTH_PROVIDER_HEADER);
        String subject = request.getHeader(AUTH_SUBJECT_HEADER);
        if (provider == null || provider.isBlank() || subject == null || subject.isBlank()) {
            throw new CatalogFailure(401, "AUTHENTICATION_REQUIRED", "Authenticated identity headers are required");
        }

        try {
            var actor = identityService.authenticate(provider, subject);
            authorizationService.requirePermission(actor, permission);
            return actor.userId();
        } catch (UnknownIdentityException error) {
            throw new CatalogFailure(401, "AUTHENTICATION_REQUIRED", error.getMessage());
        } catch (AuthorizationDeniedException error) {
            throw new CatalogFailure(403, "AUTHORIZATION_DENIED", error.getMessage());
        }
    }

    @Override
    public void audit(String actorId, String action, String entityId, String provenanceId) {
        auditService.recordCatalogEvent(actorId, action, entityId, provenanceId);
    }
}
