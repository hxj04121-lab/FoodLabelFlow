package com.spectrace.workflow.application;

import com.spectrace.identity.application.AuthorizationService;
import com.spectrace.identity.domain.AuthenticatedActor;
import com.spectrace.label.application.LabelDraftNotFoundException;
import com.spectrace.label.application.LabelVersionConflictException;
import com.spectrace.workflow.application.port.LabelReviewCommandRepository;
import com.spectrace.workflow.application.port.LabelReviewCommandRepository.PublicationTarget;
import com.spectrace.workflow.application.port.LabelReviewCommandRepository.ReviewTarget;
import com.spectrace.workflow.domain.LabelTransitionPolicy;
import com.spectrace.workflow.domain.MakerCheckerPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabelReviewService {

    private final AuthorizationService authorizationService;
    private final LabelReviewCommandRepository repository;
    private final LabelTransitionPolicy transitionPolicy;
    private final MakerCheckerPolicy makerCheckerPolicy;

    public LabelReviewService(
            AuthorizationService authorizationService,
            LabelReviewCommandRepository repository,
            LabelTransitionPolicy transitionPolicy,
            MakerCheckerPolicy makerCheckerPolicy
    ) {
        this.authorizationService = authorizationService;
        this.repository = repository;
        this.transitionPolicy = transitionPolicy;
        this.makerCheckerPolicy = makerCheckerPolicy;
    }

    @Transactional
    public void submitForReview(
            String labelVersionId,
            AuthenticatedActor actor
    ) {
        authorizationService.requirePermission(
                actor,
                "LABEL.SUBMIT_REVIEW"
        );

        ReviewTarget target =
                repository.lockForReview(labelVersionId)
                        .orElseThrow(() ->
                                new LabelDraftNotFoundException(
                                        labelVersionId
                                )
                        );

        if (!target.current()) {
            throw new LabelVersionConflictException(
                    "Label version is stale or historical: "
                            + labelVersionId
            );
        }

        if (!target.currentFormula()) {
            throw new LabelVersionConflictException(
                    "Label formula version is no longer current: "
                            + labelVersionId
            );
        }

        transitionPolicy.requireTransition(
                target.lifecycleStatus(),
                "PENDING_REVIEW"
        );

        if (!repository.hasPassingValidation(
                labelVersionId,
                target.ruleSetVersionId()
        )) {
            throw new IllegalStateException(
                    "Latest validation for the label "
                            + "and rule set must pass "
                            + "before review submission"
            );
        }

        int updated =
                repository.markPendingReview(
                        labelVersionId
                );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Label submission transition failed"
            );
        }

        repository.markReviewTaskInReview(
                labelVersionId
        );

        repository.createSubmitAudit(
                labelVersionId,
                actor.userId(),
                target.dataProvenanceId()
        );
    }

    @Transactional
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

        String permission;
        String targetStatus;

        switch (decision) {
            case "APPROVE" -> {
                permission = "LABEL.APPROVE";
                targetStatus = "APPROVED";
            }
            case "REQUEST_CHANGES" -> {
                permission = "LABEL.REQUEST_CHANGES";
                targetStatus = "DRAFT";
            }
            case "REJECT" -> {
                permission = "LABEL.REJECT";
                targetStatus = "REJECTED";
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported workflow decision: " + decision
            );
        }

        authorizationService.requirePermission(
                actor,
                permission
        );

        var target = repository.lockForDecision(labelVersionId)
                .orElseThrow(() ->
                        new LabelDraftNotFoundException(
                                labelVersionId
                        )
                );

        if (!target.current()) {
            throw new LabelVersionConflictException(
                    "Label version is stale or historical: "
                            + labelVersionId
            );
        }

        if (!target.currentFormula()) {
            throw new LabelVersionConflictException(
                    "Label formula version is no longer current: "
                            + labelVersionId
            );
        }

        transitionPolicy.requireTransition(
                target.lifecycleStatus(),
                targetStatus
        );

        if (target.reviewTaskId() == null) {
            throw new IllegalStateException(
                    "Pending ReviewTask is required"
            );
        }

        if ("APPROVE".equals(decision)) {
            makerCheckerPolicy.requireIndependentChecker(
                    target.creatorUserId(),
                    actor.userId()
            );
        }

        int updated = repository.updateDecisionState(
                labelVersionId,
                targetStatus
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Label decision transition failed"
            );
        }

        boolean resolved = "REJECT".equals(decision);
        String reviewTaskStatus = switch (decision) {
            case "REQUEST_CHANGES" -> "OPEN";
            case "APPROVE" -> "IN_REVIEW";
            case "REJECT" -> "CLOSED";
            default -> throw new IllegalStateException(
                    "Unsupported workflow decision: " + decision
            );
        };

        int taskUpdated = repository.updateReviewTaskStatus(
                target.reviewTaskId(),
                labelVersionId,
                reviewTaskStatus,
                decision,
                actor.userId(),
                resolved
        );

        if (taskUpdated != 1) {
            throw new IllegalStateException(
                    "ReviewTask decision transition failed"
            );
        }

        repository.createApprovalRecord(
                labelVersionId,
                target.reviewTaskId(),
                decision,
                actor.userId(),
                comments,
                target.dataProvenanceId()
        );

        repository.createDecisionAudit(
                labelVersionId,
                decision,
                target.lifecycleStatus(),
                targetStatus,
                actor.userId(),
                target.dataProvenanceId()
        );
    }

    @Transactional
    public void publishReviewTask(
            String reviewTaskId,
            String labelVersionId,
            AuthenticatedActor actor
    ) {
        authorizationService.requirePermission(actor, "LABEL.PUBLISH");

        PublicationTarget task = repository.lockForPublication(reviewTaskId)
                .orElseThrow(() -> new IllegalStateException(
                        "ReviewTask or its target LabelVersion was not found: "
                                + reviewTaskId
                ));

        if (!task.targetLabelVersionId().equals(labelVersionId)) {
            throw new LabelVersionConflictException(
                    "ReviewTask " + reviewTaskId
                            + " targets LabelVersion "
                            + task.targetLabelVersionId()
                            + ", not " + labelVersionId
            );
        }
        if (task.resolvedAt() != null || "CLOSED".equals(task.taskStatus())) {
            throw new IllegalStateException(
                    "Resolved ReviewTask cannot be published: " + reviewTaskId
            );
        }
        if (!"APPROVE".equals(task.decision())) {
            throw new IllegalStateException(
                    "ReviewTask must have final APPROVE decision before publication"
            );
        }
        if (!"APPROVED".equals(task.lifecycleStatus())) {
            throw new IllegalStateException(
                    "Target LabelVersion must be APPROVED before publication"
            );
        }

        repository.supersedePublishedVersion(labelVersionId);

        if (repository.publishApprovedVersion(labelVersionId) != 1) {
            throw new IllegalStateException(
                    "Publication state transition failed"
            );
        }
        if (repository.updateCurrentPublishedVersion(
                task.productId(),
                labelVersionId
        ) != 1) {
            throw new IllegalStateException(
                    "Current published LabelVersion pointer update failed"
            );
        }

        repository.createPublicationRecord(
                labelVersionId,
                actor.userId(),
                task.dataProvenanceId()
        );
        repository.createPublicationAudit(
                labelVersionId,
                reviewTaskId,
                actor.userId(),
                task.dataProvenanceId()
        );
        if (repository.resolvePublishedReviewTask(
                reviewTaskId,
                labelVersionId,
                actor.userId()
        ) != 1) {
            throw new IllegalStateException(
                    "ReviewTask resolution failed after publication"
            );
        }
    }
}
