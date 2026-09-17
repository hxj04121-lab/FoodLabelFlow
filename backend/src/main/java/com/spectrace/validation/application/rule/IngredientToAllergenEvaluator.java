package com.spectrace.validation.application.rule;

import com.spectrace.allergen.application.port.AllergenFact;
import com.spectrace.label.application.port.LabelValidationSnapshot;
import com.spectrace.validation.domain.RuleDefinition;
import com.spectrace.validation.domain.RuleType;
import com.spectrace.validation.domain.ValidationFinding;

import java.util.List;
import java.util.Objects;

/** Compares formula-derived allergen facts with structured label declarations. */
public final class IngredientToAllergenEvaluator implements RuleEvaluator {
    @Override
    public RuleType ruleType() {
        return RuleType.INGREDIENT_TO_ALLERGEN;
    }

    @Override
    public List<ValidationFinding> evaluate(RuleDefinition rule, RuleEvaluationContext context) {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(context, "context");
        if (rule.ruleType() != ruleType()) {
            throw new IllegalArgumentException("Rule type does not match ingredient evaluator");
        }
        String targetAllergenId = rule.targetAllergenId();
        if (targetAllergenId == null || targetAllergenId.isBlank()) {
            throw new IllegalStateException("Ingredient rule targetAllergenId is missing");
        }
        AllergenFact fact = context.allergens().facts().stream()
                .filter(candidate -> candidate.allergenId().equals(targetAllergenId))
                .findFirst()
                .orElse(null);
        if (fact == null) {
            return List.of(ValidationFinding.forRule(
                    rule,
                    rule.ruleCode() + "_NOT_DERIVED",
                    true,
                    "No " + targetAllergenId + " allergen was derived from the formula"));
        }

        boolean declared = context.label().declarations().stream()
                .map(LabelValidationSnapshot.AllergenDeclaration::allergenId)
                .anyMatch(targetAllergenId::equals);
        String resultCode = fact.allergenCode()
                + (declared ? "_DECLARATION_PRESENT" : "_DECLARATION_MISSING");
        String message = declared
                ? fact.allergenCode() + " is derived and declared"
                : fact.allergenCode() + " is derived but not declared";
        return List.of(ValidationFinding.forRule(rule, resultCode, declared, message));
    }
}
