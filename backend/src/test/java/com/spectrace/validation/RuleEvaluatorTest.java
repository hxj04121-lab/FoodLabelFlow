package com.spectrace.validation;

import com.spectrace.allergen.application.port.AllergenDerivation;
import com.spectrace.allergen.application.port.AllergenFact;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot;
import com.spectrace.label.application.port.LabelValidationSnapshot;
import com.spectrace.validation.application.rule.IngredientToAllergenEvaluator;
import com.spectrace.validation.application.rule.LabelDeclarationEvaluator;
import com.spectrace.validation.application.rule.RuleEvaluationContext;
import com.spectrace.validation.domain.RuleDefinition;
import com.spectrace.validation.domain.RuleSetLifecycleStatus;
import com.spectrace.validation.domain.RuleSetVersion;
import com.spectrace.validation.domain.RuleType;
import com.spectrace.validation.domain.ValidationSeverity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEvaluatorTest {
    private static final String RULE_SET_ID = "rules-1";

    @Test
    void ingredientEvaluatorUsesDerivedFactsAndKeepsFindingAttribution() {
        var rule = new RuleDefinition(
                "rule-soy", RULE_SET_ID, "INGREDIENT_SOY", RuleType.INGREDIENT_TO_ALLERGEN,
                "all_soy", "soy", ValidationSeverity.ERROR, true, "Soy declaration");
        var context = context(List.of(declaration("all_soy", "CONTAINS")), List.of(fact("all_soy", "SOY")));

        var finding = new IngredientToAllergenEvaluator().evaluate(rule, context).getFirst();

        assertThat(finding.ruleDefinitionId()).isEqualTo("rule-soy");
        assertThat(finding.resultCode()).isEqualTo("SOY_DECLARATION_PRESENT");
        assertThat(finding.passed()).isTrue();
        assertThat(finding.blocking()).isFalse();
    }

    @Test
    void ingredientEvaluatorFailsWhenAFormulaAllergenIsNotDeclared() {
        var rule = new RuleDefinition(
                "rule-soy", RULE_SET_ID, "INGREDIENT_SOY", RuleType.INGREDIENT_TO_ALLERGEN,
                "all_soy", "soy", ValidationSeverity.ERROR, true, "Soy declaration");
        var context = context(List.of(), List.of(fact("all_soy", "SOY")));

        var finding = new IngredientToAllergenEvaluator().evaluate(rule, context).getFirst();

        assertThat(finding.resultCode()).isEqualTo("SOY_DECLARATION_MISSING");
        assertThat(finding.passed()).isFalse();
        assertThat(finding.blocking()).isTrue();
    }

    @Test
    void labelDeclarationEvaluatorRejectsMissingAndMayContainDeclarations() {
        var rule = new RuleDefinition(
                "rule-label", RULE_SET_ID, "LABEL_CONTAINS_DECLARATION",
                RuleType.LABEL_DECLARATION_VALIDATION, null,
                "label declaration type must be CONTAINS for MVP", ValidationSeverity.ERROR, true,
                "Declaration shape");
        var evaluator = new LabelDeclarationEvaluator();

        var missing = evaluator.evaluate(rule, context(List.of(), List.of())).getFirst();
        var mayContain = evaluator.evaluate(
                rule, context(List.of(declaration("all_soy", "MAY_CONTAIN")), List.of())).getFirst();

        assertThat(missing.resultCode()).isEqualTo("LABEL_DECLARATION_MISSING");
        assertThat(missing.blocking()).isTrue();
        assertThat(mayContain.resultCode()).isEqualTo("LABEL_DECLARATION_TYPE_INVALID");
        assertThat(mayContain.passed()).isFalse();
    }

    private static RuleEvaluationContext context(
            List<LabelValidationSnapshot.AllergenDeclaration> declarations,
            List<AllergenFact> facts
    ) {
        var label = new LabelValidationSnapshot(
                "label-1", "product-1", "formula-1", RULE_SET_ID, "US", "",
                true, "prov-label", declarations);
        var ruleSet = new RuleSetVersion(
                RULE_SET_ID, "TEST_RULES", "1", "US", RuleSetLifecycleStatus.ACTIVE,
                LocalDate.of(2026, 1, 1), null, true, "Test rules", "prov-rules", List.of());
        var derivation = new AllergenDerivation("formula-1", RULE_SET_ID, "US", facts, List.of());
        return new RuleEvaluationContext(label, ruleSet, derivation);
    }

    private static LabelValidationSnapshot.AllergenDeclaration declaration(
            String allergenId, String declarationType) {
        return new LabelValidationSnapshot.AllergenDeclaration(
                allergenId, declarationType, "USER_ENTERED", null);
    }

    private static AllergenFact fact(String allergenId, String allergenCode) {
        return new AllergenFact(allergenId, allergenCode, List.of(
                new AllergenFact.DerivationEvidence(
                        "item-1", "spec-1", "component-1", "ingredient-1",
                        "mapping-1", "fixture mapping", "prov-formula")));
    }
}
