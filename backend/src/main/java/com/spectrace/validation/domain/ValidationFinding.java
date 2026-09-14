package com.spectrace.validation.domain;

import java.util.Objects;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/** An evaluation outcome before M5 assigns run/result persistence identifiers. */
public record ValidationFinding(
        String ruleDefinitionId,
        String resultCode,
        ValidationSeverity severity,
        boolean passed,
        boolean blocking,
        String message
) {
    public ValidationFinding {
        if (ruleDefinitionId != null) {
            ruleDefinitionId = requiredText(ruleDefinitionId, "ruleDefinitionId");
        }
        resultCode = requiredText(resultCode, "resultCode");
        severity = Objects.requireNonNull(severity, "severity");
        message = requiredText(message, "message");
        if (blocking && (passed || severity != ValidationSeverity.ERROR)) {
            throw new IllegalArgumentException("Only a failed ERROR finding can block validation");
        }
    }

    /** Rule strategies retain attribution and consistently make failed ERRORs blocking. */
    public static ValidationFinding forRule(
            RuleDefinition rule, String resultCode, boolean passed, String message) {
        return new ValidationFinding(rule.ruleDefinitionId(), resultCode, rule.severity(),
                passed, !passed && rule.severity() == ValidationSeverity.ERROR, message);
    }
}
