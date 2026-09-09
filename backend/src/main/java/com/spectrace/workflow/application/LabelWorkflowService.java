package com.spectrace.workflow.application;

import com.spectrace.identity.application.AuthorizationService;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.workflow.application.port.LabelWorkflowRepository;
import org.springframework.stereotype.Service;

@Service
public class LabelWorkflowService {

    private final AuthorizationService authorizationService;
    private final LabelWorkflowRepository workflowRepository;

    public LabelWorkflowService(
            AuthorizationService authorizationService,
            LabelWorkflowRepository workflowRepository
    ) {
        this.authorizationService = authorizationService;
        this.workflowRepository = workflowRepository;
    }

    public void submitForReview(
            String labelVersionId,
            AuthenticatedActor actor
    ) {
        authorizationService.requirePermission(
                actor,
                "LABEL.SUBMIT_REVIEW"
        );

        workflowRepository.submitForReview(
                labelVersionId,
                actor.userId()
        );
    }

    public void recordDecision(
            String labelVersionId,
            String decision,
            String comments,
            AuthenticatedActor actor
    ) {
        String permission = switch (decision) {
            case "APPROVE" -> "LABEL.APPROVE";
            case "REJECT" -> "LABEL.REJECT";
            case "REQUEST_CHANGES" -> "LABEL.REQUEST_CHANGES";
            default -> throw new IllegalArgumentException(
                    "Unsupported workflow decision: " + decision
            );
        };

        authorizationService.requirePermission(actor, permission);

        workflowRepository.recordDecision(
                labelVersionId,
                decision,
                actor.userId(),
                comments
        );
    }
}