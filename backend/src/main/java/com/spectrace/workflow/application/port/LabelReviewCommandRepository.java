package com.spectrace.workflow.application.port;

import java.util.Optional;

public interface LabelReviewCommandRepository {

    Optional<ReviewTarget> lockForReview(
            String labelVersionId
    );

    boolean hasPassingValidation(
            String labelVersionId,
            String ruleSetVersionId
    );

    int markPendingReview(
            String labelVersionId
    );

    void markReviewTaskInReview(
            String labelVersionId
    );

    void createSubmitAudit(
            String labelVersionId,
            String actorUserId,
            String dataProvenanceId
    );


    Optional<DecisionTarget> lockForDecision(
            String labelVersionId
    );

    int updateDecisionState(
            String labelVersionId,
            String newStatus
    );

    void createApprovalRecord(
            String labelVersionId,
            String reviewTaskId,
            String decision,
            String actorUserId,
            String comments,
            String dataProvenanceId
    );

    void createDecisionAudit(
            String labelVersionId,
            String decision,
            String beforeStatus,
            String afterStatus,
            String actorUserId,
            String dataProvenanceId
    );

    record DecisionTarget(
            String labelVersionId,
            String lifecycleStatus,
            String creatorUserId,
            String reviewTaskId,
            boolean current,
            String dataProvenanceId
    ) {
    }
    record ReviewTarget(
            String labelVersionId,
            String ruleSetVersionId,
            String lifecycleStatus,
            boolean current,
            boolean currentFormula,
            String dataProvenanceId
    ) {
    }
}
