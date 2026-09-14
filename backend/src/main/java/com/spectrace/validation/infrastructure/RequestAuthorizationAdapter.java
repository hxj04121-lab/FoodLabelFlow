package com.spectrace.validation.infrastructure;

import com.spectrace.audit.application.port.AuditEventPort;
import com.spectrace.identity.application.AuthorizationService;
import com.spectrace.identity.application.IdentityService;
import com.spectrace.validation.application.port.ValidationIntegration;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** M4 bridge for trusted request identity and same-transaction validation audit. */
@Component
public class RequestAuthorizationAdapter implements ValidationIntegration {

    public static final String AUTH_PROVIDER_HEADER = "X-Auth-Provider";
    public static final String AUTH_SUBJECT_HEADER = "X-External-Subject";
    public static final String VALIDATE_LABEL_PERMISSION = "LABEL.VALIDATE";

    private final HttpServletRequest request;
    private final IdentityService identityService;
    private final AuthorizationService authorizationService;
    private final AuditEventPort auditEvents;

    public RequestAuthorizationAdapter(
            HttpServletRequest request,
            IdentityService identityService,
            AuthorizationService authorizationService,
            AuditEventPort auditEvents
    ) {
        this.request = request;
        this.identityService = identityService;
        this.authorizationService = authorizationService;
        this.auditEvents = auditEvents;
    }

    @Override
    public String requireActor(String permission) {
        String provider = request.getHeader(AUTH_PROVIDER_HEADER);
        String subject = request.getHeader(AUTH_SUBJECT_HEADER);
        if (provider == null || provider.isBlank() || subject == null || subject.isBlank()) {
            throw new com.spectrace.identity.application.UnknownIdentityException(
                    "Authenticated identity headers are required");
        }

        var actor = identityService.authenticate(provider, subject);
        authorizationService.requirePermission(actor, permission);
        return actor.userId();
    }

    @Override
    public void auditValidation(
            String actorId,
            String labelVersionId,
            String ruleSetVersionId,
            String validationRunId,
            String dataProvenanceId
    ) {
        auditEvents.recordValidationEvent(
                actorId, labelVersionId, validationRunId, ruleSetVersionId, dataProvenanceId);
    }
}
