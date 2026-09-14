package com.spectrace.validation.application.rule;

import com.spectrace.allergen.application.port.AllergenDerivation;
import com.spectrace.label.application.port.LabelValidationSnapshot;
import com.spectrace.validation.domain.RuleSetVersion;

import java.util.Objects;

/** Construct only after application precondition checks; never mix input versions. */
public record RuleEvaluationContext(
        LabelValidationSnapshot label,
        RuleSetVersion ruleSet,
        AllergenDerivation allergens
) {
    public RuleEvaluationContext {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(ruleSet, "ruleSet");
        Objects.requireNonNull(allergens, "allergens");
        if (!label.formulaVersionId().equals(allergens.formulaVersionId())
                || !label.ruleSetVersionId().equals(ruleSet.ruleSetVersionId())
                || !ruleSet.ruleSetVersionId().equals(allergens.ruleSetVersionId())
                || !label.jurisdictionCode().equals(ruleSet.jurisdictionCode())
                || !ruleSet.jurisdictionCode().equals(allergens.jurisdictionCode())) {
            throw new IllegalArgumentException("Label, rule set and allergen input versions must agree");
        }
    }
}
