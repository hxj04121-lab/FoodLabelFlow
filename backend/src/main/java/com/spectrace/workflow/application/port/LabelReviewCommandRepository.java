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

    int updateReviewTaskStatus(
            String reviewTaskId,
            String labelVersionId,
            String newStatus,
            String decision,
            String resolverUserId,
            boolean resolved
    );

    Optional<PublicationTarget> lockForPublication(String reviewTaskId);

    int supersedePublishedVersion(String labelVersionId);

    int publishApprovedVersion(String labelVersionId);

    int updateCurrentPublishedVersion(String productId, String labelVersionId);

    void createPublicationRecord(
            String labelVersionId,
            String actorUserId,
            String dataProvenanceId
    );

    void createPublicationAudit(
            String labelVersionId,
            String reviewTaskId,
            String actorUserId,
            String dataProvenanceId
    );

    int resolvePublishedReviewTask(
            String reviewTaskId,
            String labelVersionId,
            String resolverUserId
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
            boolean currentFormula,
            String ruleSetVersionId,
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

    record PublicationTarget(
            String reviewTaskId,
            String targetLabelVersionId,
            String decision,
            String taskStatus,
            java.time.LocalDateTime resolvedAt,
            String lifecycleStatus,
            String productId,
            String dataProvenanceId,
            boolean currentFormula,
            boolean latestLabelVersion,
            boolean hasApproveRecord,
            boolean hasPassingValidation
    ) {
    }
}
