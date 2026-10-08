package com.spectrace.impact.infrastructure;

import com.spectrace.audit.application.port.ImpactAuditPort;
import com.spectrace.identity.application.AuthorizationService;
import com.spectrace.identity.application.IdentityService;
import com.spectrace.identity.application.ExternalActorResolver;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.impact.application.port.ImpactIntegration;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** M4 bridge for trusted request identity and same-transaction impact audit. */
@Component
public class RequestImpactIntegration implements ImpactIntegration {

    private static final String AUTH_PROVIDER_HEADER = "X-Auth-Provider";
    private static final String AUTH_SUBJECT_HEADER = "X-External-Subject";
    /** Proposed with M4 in SCRUM-79: the maker who drafts the replacement label owns the task. */
    static final String REVIEW_TASK_ASSIGNEE_PERMISSION = "LABEL.CREATE";

    private final HttpServletRequest request;
    private final IdentityService identityService;
    private final ExternalActorResolver actors;
    private final AuthorizationService authorizationService;
    private final ImpactAuditPort auditEvents;

    public RequestImpactIntegration(
            HttpServletRequest request,
            IdentityService identityService,
            AuthorizationService authorizationService,
            ImpactAuditPort auditEvents
    ) {
        this(request, identityService, authorizationService, auditEvents,
                new ExternalActorResolver(identityService, false));
    }

    @Autowired
    public RequestImpactIntegration(
            HttpServletRequest request,
            IdentityService identityService,
            AuthorizationService authorizationService,
            ImpactAuditPort auditEvents,
            ExternalActorResolver actors
    ) {
        this.request = request;
        this.identityService = identityService;
        this.actors = actors;
        this.authorizationService = authorizationService;
        this.auditEvents = auditEvents;
    }

    @Override
    public String requireActor(Permission permission) {
        AuthenticatedActor actor = actor();
        authorizationService.requirePermission(actor, permission.code());
        return actor.userId();
    }

    @Override
    public String authenticate() {
        return actor().userId();
    }

    @Override
    public void auditChangeRequestCreated(String actorId, String changeRequestId, String dataProvenanceId) {
        auditEvents.recordImpactEvent(actorId, "CHANGE_REQUEST_CREATED", "CHANGE_REQUEST",
                changeRequestId, changeRequestId, dataProvenanceId);
    }

    @Override
    public void auditImpactRun(
            String actorId, String changeRequestId, String impactAnalysisRunId, String dataProvenanceId) {
        auditEvents.recordImpactEvent(actorId, "IMPACT_ANALYSIS_RUN", "IMPACT_ANALYSIS_RUN",
                impactAnalysisRunId, changeRequestId, dataProvenanceId);
    }

    @Override
    public String reviewTaskAssignee() {
        return identityService.activeUserIdsWithPermission(REVIEW_TASK_ASSIGNEE_PERMISSION).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No active user holds " + REVIEW_TASK_ASSIGNEE_PERMISSION + " to receive review tasks"));
    }

    private AuthenticatedActor actor() {
        String provider = request.getHeader(AUTH_PROVIDER_HEADER);
        String subject = request.getHeader(AUTH_SUBJECT_HEADER);
        return actors.resolve(provider, subject);
    }
}
