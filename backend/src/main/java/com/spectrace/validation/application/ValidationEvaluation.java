package com.spectrace.validation.application;

import com.spectrace.allergen.application.port.AllergenDerivation;
import com.spectrace.label.application.port.LabelValidationSnapshot;
import com.spectrace.validation.domain.RuleSetVersion;
import com.spectrace.validation.domain.ValidationFinding;
import com.spectrace.validation.domain.ValidationStatus;

import java.util.List;
import java.util.Objects;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/** Immutable in-memory outcome handed to the persistence slice in SCRUM-44. */
public record ValidationEvaluation(
        String actorId,
        LabelValidationSnapshot label,
        RuleSetVersion ruleSet,
        AllergenDerivation allergens,
        List<ValidationFinding> findings,
        ValidationStatus status
) {
    public ValidationEvaluation {
        actorId = requiredText(actorId, "actorId");
        label = Objects.requireNonNull(label, "label");
        ruleSet = Objects.requireNonNull(ruleSet, "ruleSet");
        allergens = Objects.requireNonNull(allergens, "allergens");
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        if (findings.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("findings must not contain null entries");
        }
        status = Objects.requireNonNull(status, "status");
        ValidationStatus expected = findings.stream()
                .anyMatch(finding -> finding.blocking() && !finding.passed())
                ? ValidationStatus.FAILED
                : ValidationStatus.PASSED;
        if (status != expected) {
            throw new IllegalArgumentException("status must agree with blocking findings");
        }
    }
}
