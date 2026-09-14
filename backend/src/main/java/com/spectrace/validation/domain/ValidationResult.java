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
        resultCode = required(resultCode, "resultCode");
        severity = Objects.requireNonNull(severity, "severity");
        message = required(message, "message");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
