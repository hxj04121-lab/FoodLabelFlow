package com.spectrace.validation.domain;

import java.time.Instant;
import java.util.Objects;

public record ValidationRun(
        String validationRunId,
        String labelVersionId,
        String ruleSetVersionId,
        ValidationStatus status,
        String ranByUserId,
        Instant ranAt,
        String summary,
        String dataProvenanceId
) {
    public ValidationRun {
        validationRunId = required(validationRunId, "validationRunId");
        labelVersionId = required(labelVersionId, "labelVersionId");
        ruleSetVersionId = required(ruleSetVersionId, "ruleSetVersionId");
        status = Objects.requireNonNull(status, "status");
        ranByUserId = required(ranByUserId, "ranByUserId");
        ranAt = Objects.requireNonNull(ranAt, "ranAt");
        dataProvenanceId = required(dataProvenanceId, "dataProvenanceId");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
