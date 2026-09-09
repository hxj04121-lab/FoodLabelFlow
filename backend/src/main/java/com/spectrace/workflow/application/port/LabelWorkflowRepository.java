package com.spectrace.workflow.application.port;

public interface LabelWorkflowRepository {

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
}