package com.spectrace.workflow.application.port;

import java.util.Optional;

public interface LabelPublicationRepository {

    Optional<PublicationTarget> lockForPublication(
            String labelVersionId
    );

    boolean hasApproveRecord(
            String labelVersionId
    );

    void supersedeCurrentPublished(
            String productId,
            String jurisdictionCode
    );

    int markPublished(
            String labelVersionId
    );

    void updateCurrentPublishedPointer(
            String productId,
            String labelVersionId
    );

    void linkAndResolveReviewTask(
            String labelVersionId
    );

    void createPublicationRecord(
            String labelVersionId,
            String actorUserId,
            String dataProvenanceId
    );

    void createPublicationAudit(
            String labelVersionId,
            String actorUserId,
            String dataProvenanceId
    );

    record PublicationTarget(
            String labelVersionId,
            String productId,
            String formulaVersionId,
            String currentFormulaVersionId,
            String jurisdictionCode,
            String lifecycleStatus,
            String dataProvenanceId
    ) {
    }
}