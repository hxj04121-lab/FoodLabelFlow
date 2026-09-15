package com.spectrace.validation.domain;

import java.util.Objects;

public record ValidationResult(
        String validationResultId,
        String validationRunId,
        String ruleDefinitionId,
        String resultCode,
        ValidationSeverity severity,
        boolean passed,
        boolean blocking,
        String message
) {
    public ValidationResult {
        validationResultId = required(validationResultId, "validationResultId");
        validationRunId = required(validationRunId, "validationRunId");
        if (ruleDefinitionId != null && ruleDefinitionId.isBlank()) {
            throw new IllegalArgumentException("ruleDefinitionId must be null or non-blank");
        }
        resultCode = required(resultCode, "resultCode");
        severity = Objects.requireNonNull(severity, "severity");
        message = required(message, "message");
        if (blocking && (passed || severity != ValidationSeverity.ERROR)) {
            throw new IllegalArgumentException("Only a failed ERROR result can block validation");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
