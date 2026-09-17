package com.spectrace.validation.application.rule;

import com.spectrace.catalog.application.port.FormulaCompositionSnapshot.MatchStatus;
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
        if (rule.targetAllergenId() != null) {
            return List.of(AllergenDeclarationFindings.evaluate(rule, context));
        }

        // A null target is valid only for the explicit unresolved-component rules.
        // It is not a catch-all fallback for malformed allergen rules.
        MatchStatus matchStatus = switch (rule.patternText()) {
            case "UNMAPPED" -> MatchStatus.UNMAPPED;
            case "AMBIGUOUS" -> MatchStatus.AMBIGUOUS;
            default -> throw new IllegalStateException(
                    "Ingredient rule without a target must specify UNMAPPED or AMBIGUOUS");
        };
        List<ValidationFinding> findings = context.allergens().unresolvedComponents().stream()
                .filter(component -> component.matchStatus() == matchStatus)
                .map(component -> ValidationFinding.forRule(rule, "INGREDIENT_" + matchStatus, false,
                        "Ingredient phrase '" + component.rawPhrase() + "' "
                                + (matchStatus == MatchStatus.UNMAPPED
                                ? "is unmapped." : "has multiple canonical matches.")))
                .toList();
        if (findings.isEmpty()) {
            return List.of(ValidationFinding.forRule(
                    rule, "INGREDIENT_" + matchStatus + "_ABSENT", true,
                    "No formula components have match status " + matchStatus + "."));
        }
        return findings;
    }
}
