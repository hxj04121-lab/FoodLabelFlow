package com.spectrace.impact.domain;

import java.time.Instant;
import java.util.Objects;

public record ImpactAnalysisRun(
        String impactAnalysisRunId,
        String runCode,
        String changeRequestId,
        String ruleSetVersionId,
        ImpactRunStatus status,
        Instant startedAt,
        Instant completedAt,
        String executedByUserId,
        String dataProvenanceId,
        String idempotencyKey
) {
    public ImpactAnalysisRun {
        impactAnalysisRunId = required(impactAnalysisRunId, "impactAnalysisRunId");
        runCode = required(runCode, "runCode");
        changeRequestId = required(changeRequestId, "changeRequestId");
        ruleSetVersionId = required(ruleSetVersionId, "ruleSetVersionId");
        status = Objects.requireNonNull(status, "status");
        startedAt = Objects.requireNonNull(startedAt, "startedAt");
        executedByUserId = required(executedByUserId, "executedByUserId");
        dataProvenanceId = required(dataProvenanceId, "dataProvenanceId");
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
