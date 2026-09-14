package com.spectrace.audit.application.port;

public interface AuditEventPort {

    void recordValidationEvent(
            String actorId,
            String labelVersionId,
            String validationRunId,
            String ruleSetVersionId,
            String provenanceId
    );
}
