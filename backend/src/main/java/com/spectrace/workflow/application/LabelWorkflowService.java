package com.spectrace.workflow.application;

import com.spectrace.identity.application.AuthorizationService;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.workflow.application.port.LabelWorkflowRepository;
import com.spectrace.workflow.domain.MakerCheckerPolicy;
import org.springframework.stereotype.Service;

@Service
public class LabelWorkflowService {

    private final AuthorizationService authorizationService;
    private final LabelWorkflowRepository workflowRepository;
    private final MakerCheckerPolicy makerCheckerPolicy;

    public LabelWorkflowService(
            AuthorizationService authorizationService,
            LabelWorkflowRepository workflowRepository,
            MakerCheckerPolicy makerCheckerPolicy
    ) {
        this.authorizationService = authorizationService;
        this.workflowRepository = workflowRepository;
        this.makerCheckerPolicy = makerCheckerPolicy;
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
        if (decision == null || decision.isBlank()) {
            throw new IllegalArgumentException(
                    "Workflow decision is required"
            );
        }

        String permission = switch (decision) {
            case "APPROVE" -> "LABEL.APPROVE";
            case "REJECT" -> "LABEL.REJECT";
            case "REQUEST_CHANGES" -> "LABEL.REQUEST_CHANGES";
            default -> throw new IllegalArgumentException(
                    "Unsupported workflow decision: " + decision
            );
        };

        authorizationService.requirePermission(actor, permission);

        if ("APPROVE".equals(decision)) {
            String creatorUserId = workflowRepository
                    .findCreatorUserId(labelVersionId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Label version not found: " + labelVersionId
                    ));

            makerCheckerPolicy.requireIndependentChecker(
                    creatorUserId,
                    actor.userId()
            );
        }

        workflowRepository.recordDecision(
                labelVersionId,
                decision,
                actor.userId(),
                comments
        );
    }
}