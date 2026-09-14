package com.spectrace.validation.application.contract;

import com.spectrace.validation.domain.ValidationSeverity;

import java.util.Objects;

/** Application-boundary finding; its fields map directly to ValidationResult. */
public record ValidationFinding(
        String ruleDefinitionId,
        String resultCode,
        ValidationSeverity severity,
        boolean passed,
        boolean blocking,
        String message
) {

    public ValidationFinding {
        if (ruleDefinitionId != null && ruleDefinitionId.isBlank()) {
            throw new IllegalArgumentException("ruleDefinitionId must be null or non-blank");
        }
        resultCode = required(resultCode, "resultCode");
        severity = Objects.requireNonNull(severity, "severity");
        message = required(message, "message");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
