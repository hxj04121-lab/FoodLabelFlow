package com.spectrace.validation.application.rule;

import com.spectrace.label.application.port.LabelValidationSnapshot;
import com.spectrace.validation.domain.RuleDefinition;
import com.spectrace.validation.domain.RuleType;
import com.spectrace.validation.domain.ValidationFinding;

import java.util.List;
import java.util.Objects;

/** Validates the MVP structured declaration shape required by the active rule. */
public final class LabelDeclarationEvaluator implements RuleEvaluator {
    private static final String CONTAINS = "CONTAINS";

    @Override
    public RuleType ruleType() {
        return RuleType.LABEL_DECLARATION_VALIDATION;
    }

    @Override
    public List<ValidationFinding> evaluate(RuleDefinition rule, RuleEvaluationContext context) {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(context, "context");
        if (rule.ruleType() != ruleType()) {
            throw new IllegalArgumentException("Rule type does not match label declaration evaluator");
        }
        if (rule.targetAllergenId() != null) {
            return List.of(AllergenDeclarationFindings.evaluate(rule, context));
        }

        List<LabelValidationSnapshot.AllergenDeclaration> declarations = context.label().declarations();
        boolean present = !declarations.isEmpty();
        boolean allContains = present && declarations.stream()
                .allMatch(declaration -> CONTAINS.equals(declaration.declarationType()));
        boolean passed = present && allContains;
        String resultCode = passed
                ? "LABEL_DECLARATION_PRESENT"
                : present ? "LABEL_DECLARATION_TYPE_INVALID" : "ALLERGEN_DECLARATION_MISSING";
        String message = passed
                ? "Structured allergen declarations are present and use CONTAINS"
                : present
                ? "Every structured allergen declaration must use CONTAINS"
                : "Structured allergen declaration is missing";
        return List.of(ValidationFinding.forRule(rule, resultCode, passed, message));
    }
}
