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
import com.spectrace.validation.domain.ValidationFinding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        assertThat(finding.resultCode()).isEqualTo("ALLERGEN_DECLARATION_MISSING");
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

        assertThat(missing.resultCode()).isEqualTo("ALLERGEN_DECLARATION_MISSING");
        assertThat(missing.blocking()).isTrue();
        assertThat(mayContain.resultCode()).isEqualTo("LABEL_DECLARATION_TYPE_INVALID");
        assertThat(mayContain.passed()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(RuleType.class)
    void targetedDeclarationsRequireContainsForTheExactDerivedAllergen(RuleType type) {
        var rule = new RuleDefinition("rule-soy", RULE_SET_ID, "SOY_DECLARATION", type,
                "all_soy", "SOY", ValidationSeverity.ERROR, true, "Soy declaration");
        var evaluator = type == RuleType.INGREDIENT_TO_ALLERGEN
                ? new IngredientToAllergenEvaluator() : new LabelDeclarationEvaluator();
        var facts = List.of(fact("all_soy", "SOY"));
        var wrongAllergen = context(List.of(declaration("all_milk", "CONTAINS")), facts);
        var mayContain = context(List.of(declaration("all_soy", "MAY_CONTAIN")), facts);
        for (var input : List.of(wrongAllergen, mayContain)) {
            assertThat(evaluator.evaluate(rule, input)).containsExactly(new ValidationFinding(
                    "rule-soy", "ALLERGEN_DECLARATION_MISSING", ValidationSeverity.ERROR, false, true,
                    "SOY is derived but no CONTAINS declaration is present."));
        }
        assertThat(evaluator.evaluate(rule, context(List.of(), List.of())))
                .singleElement().satisfies(finding -> {
                    assertThat(finding.resultCode()).isEqualTo("SOY_DECLARATION_NOT_DERIVED");
                    assertThat(finding.passed()).isTrue();
                    assertThat(finding.blocking()).isFalse();
                });
    }

    @ParameterizedTest
    @EnumSource(value = FormulaCompositionSnapshot.MatchStatus.class, names = {"UNMAPPED", "AMBIGUOUS"})
    void unresolvedRulesPreserveEveryMatchingComponentAndRuleSeverity(
            FormulaCompositionSnapshot.MatchStatus matchStatus) {
        var rule = new RuleDefinition("rule-unresolved", RULE_SET_ID, "INGREDIENT_" + matchStatus,
                RuleType.INGREDIENT_TO_ALLERGEN, null, matchStatus.name(),
                ValidationSeverity.WARNING, true, "Unresolved ingredients");
        var base = context(List.of(), List.of(fact("all_soy", "SOY")));
        var components = List.of(
                new AllergenDerivation.UnresolvedComponent("item-1", "spec-1", "component-1", "ingredient-1",
                        "First phrase", "no match", matchStatus),
                new AllergenDerivation.UnresolvedComponent("item-1", "spec-1", "component-2", "ingredient-2",
                        "Second phrase", "no match", matchStatus),
                new AllergenDerivation.UnresolvedComponent("item-1", "spec-1", "component-3", "ingredient-3",
                        "Different status", "no match", matchStatus == FormulaCompositionSnapshot.MatchStatus.UNMAPPED
                        ? FormulaCompositionSnapshot.MatchStatus.AMBIGUOUS : FormulaCompositionSnapshot.MatchStatus.UNMAPPED));
        var input = new RuleEvaluationContext(base.label(), base.ruleSet(), new AllergenDerivation(
                "formula-1", RULE_SET_ID, "US", base.allergens().facts(), components));
        var evaluator = new IngredientToAllergenEvaluator();

        assertThat(evaluator.evaluate(rule, input)).hasSize(2).allSatisfy(finding -> {
            assertThat(finding.ruleDefinitionId()).isEqualTo(rule.ruleDefinitionId());
            assertThat(finding.resultCode()).isEqualTo("INGREDIENT_" + matchStatus);
            assertThat(finding.severity()).isEqualTo(ValidationSeverity.WARNING);
            assertThat(finding.passed()).isFalse();
            assertThat(finding.blocking()).isFalse();
        });
        assertThat(evaluator.evaluate(rule, base)).singleElement().satisfies(finding -> {
            assertThat(finding.resultCode()).isEqualTo("INGREDIENT_" + matchStatus + "_ABSENT");
            assertThat(finding.passed()).isTrue();
            assertThat(finding.blocking()).isFalse();
        });
    }

    @Test
    void missingTargetIsNotASilentFallbackForAnUnrecognizedIngredientRule() {
        var rule = new RuleDefinition("rule-invalid", RULE_SET_ID, "INGREDIENT_SOY",
                RuleType.INGREDIENT_TO_ALLERGEN, null, "soy", ValidationSeverity.ERROR, true, "Soy declaration");
        assertThatThrownBy(() -> new IngredientToAllergenEvaluator().evaluate(rule, context(List.of(), List.of())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("UNMAPPED or AMBIGUOUS");
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
