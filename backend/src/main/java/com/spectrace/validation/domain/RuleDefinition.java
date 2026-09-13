package com.spectrace.validation.domain;

import java.util.Objects;

public record RuleDefinition(
        String ruleDefinitionId,
        String ruleSetVersionId,
        String ruleCode,
        RuleType ruleType,
        String targetAllergenId,
        String patternText,
        ValidationSeverity severity,
        boolean active,
        String description
) {
    public RuleDefinition {
        ruleDefinitionId = required(ruleDefinitionId, "ruleDefinitionId");
        ruleSetVersionId = required(ruleSetVersionId, "ruleSetVersionId");
        ruleCode = required(ruleCode, "ruleCode");
        ruleType = Objects.requireNonNull(ruleType, "ruleType");
        patternText = required(patternText, "patternText");
        severity = Objects.requireNonNull(severity, "severity");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
