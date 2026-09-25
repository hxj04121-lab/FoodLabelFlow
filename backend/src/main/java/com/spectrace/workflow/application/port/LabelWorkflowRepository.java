package com.spectrace.workflow.application.port;

import java.util.Optional;

public interface LabelWorkflowRepository {

    Optional<String> findCreatorUserId(String labelVersionId);

    Optional<LabelWorkflowVersion> findVersion(String labelVersionId);

    void submitForReview(
            String labelVersionId,
            String actorUserId
    );

    void recordDecision(
            String labelVersionId,
            String decision,
            String actorUserId,
            String comments
    );

    record LabelWorkflowVersion(
            String labelVersionId,
            String lifecycleStatus,
            boolean current
    ) {
    }
}