package com.spectrace.validation.application.rule;

import com.spectrace.validation.domain.RuleDefinition;
import com.spectrace.validation.domain.ValidationFinding;

/** Shared declaration policy for rules targeting a formula-derived allergen. */
final class AllergenDeclarationFindings {
    private AllergenDeclarationFindings() {
    }

    static ValidationFinding evaluate(RuleDefinition rule, RuleEvaluationContext context) {
        String targetAllergenId = rule.targetAllergenId();
        if (targetAllergenId == null || targetAllergenId.isBlank()) {
            throw new IllegalStateException("Allergen declaration rule targetAllergenId is missing");
        }
        var fact = context.allergens().facts().stream()
                .filter(candidate -> candidate.allergenId().equals(targetAllergenId))
                .findFirst();
        if (fact.isEmpty()) {
            return ValidationFinding.forRule(rule, rule.ruleCode() + "_NOT_DERIVED", true,
                    "No " + targetAllergenId + " allergen was derived from the formula");
        }

        boolean declared = context.label().declarations().stream().anyMatch(declaration ->
                targetAllergenId.equals(declaration.allergenId())
                        && "CONTAINS".equals(declaration.declarationType()));
        String allergenCode = fact.orElseThrow().allergenCode();
        return ValidationFinding.forRule(rule,
                declared ? allergenCode + "_DECLARATION_PRESENT" : "ALLERGEN_DECLARATION_MISSING",
                declared,
                declared ? allergenCode + " is derived and declared as CONTAINS."
                        : allergenCode + " is derived but no CONTAINS declaration is present.");
    }
}
