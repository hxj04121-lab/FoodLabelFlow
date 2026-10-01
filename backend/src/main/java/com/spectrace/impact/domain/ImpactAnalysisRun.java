package com.spectrace.impact.domain;

import java.time.Instant;
import java.util.Objects;

import static com.spectrace.shared.contract.ContractValues.requiredText;

public record ImpactAnalysisRun(
        String impactAnalysisRunId,
        String runCode,
        String changeRequestId,
        String ruleSetVersionId,
        ImpactRunStatus status,
        Instant startedAt,
        Instant completedAt,
        String executedByUserId,
        String dataProvenanceId
) {
    public ImpactAnalysisRun {
        impactAnalysisRunId = requiredText(impactAnalysisRunId, "impactAnalysisRunId");
        runCode = requiredText(runCode, "runCode");
        changeRequestId = requiredText(changeRequestId, "changeRequestId");
        ruleSetVersionId = requiredText(ruleSetVersionId, "ruleSetVersionId");
        status = Objects.requireNonNull(status, "status");
        startedAt = Objects.requireNonNull(startedAt, "startedAt");
        executedByUserId = requiredText(executedByUserId, "executedByUserId");
        dataProvenanceId = requiredText(dataProvenanceId, "dataProvenanceId");
        if (status.isTerminal() != (completedAt != null)) {
            throw new IllegalArgumentException("completedAt is required exactly when the run is terminal");
        }
        if (completedAt != null && completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("completedAt must not precede startedAt");
        }
    }
}
