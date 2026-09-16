package com.spectrace.audit.application.port;

public interface AuditEventPort {

    void recordValidationEvent(
            String actorId,
            String labelVersionId,
            String validationRunId,
            String ruleSetVersionId,
            String provenanceId
    );

    default void recordValidationEvent(
            String actorId,
            String labelVersionId,
            String validationRunId,
            String ruleSetVersionId,
            String provenanceId,
            String validationStatus,
            String summary
    ) {
        recordValidationEvent(actorId, labelVersionId, validationRunId, ruleSetVersionId, provenanceId);
    }
}
