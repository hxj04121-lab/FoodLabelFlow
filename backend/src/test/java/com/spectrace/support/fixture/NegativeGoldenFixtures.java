package com.spectrace.support.fixture;

import com.spectrace.allergen.application.port.AllergenDerivation;
import com.spectrace.allergen.application.port.AllergenFact;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot.MatchStatus;
import com.spectrace.label.application.port.LabelValidationSnapshot;
import com.spectrace.shared.api.ApiError;
import com.spectrace.validation.domain.ValidationFinding;
import com.spectrace.validation.domain.ValidationSeverity;
import com.spectrace.validation.domain.ValidationStatus;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic negative truth data for SCRUM-13/SCRUM-24.
 *
 * <p>The values use owner application-port contracts directly so M1 can reuse them
 * in rule/orchestrator tests and M5 can load the companion SQL in an isolated
 * integration/API harness. A failed completed evaluation is deliberately distinct
 * from a precondition {@link ApiError}; neither path has a fallback PASS.</p>
 */
public final class NegativeGoldenFixtures {

    public static final String SQL_RESOURCE = "/fixtures/s2-m2-negative-golden-fixtures.sql";
    public static final String MISSING_DECLARATION_RULE_SET_VERSION_ID =
            "ruleset_s2_m2_negative_missing_decl_v1";
    public static final String UNMAPPED_RULE_SET_VERSION_ID =
            "ruleset_s2_m2_negative_unmapped_v1";
    public static final String AMBIGUOUS_RULE_SET_VERSION_ID =
            "ruleset_s2_m2_negative_ambiguous_v1";
    public static final String INACTIVE_RULE_SET_VERSION_ID = "ruleset_s2_m2_negative_retired_v1";
    public static final String JURISDICTION_CODE = "US";
    public static final String PROVENANCE_ID = "prov_s2_m2_negative_v1";

    public static final String MISSING_DECLARATION_ID = "S2M2-NEG-MISSING-DECL-001";
    public static final String NO_ACTIVE_RULE_SET_ID = "S2M2-NEG-NO-ACTIVE-RULESET-001";
    public static final String UNMAPPED_INGREDIENT_ID = "S2M2-NEG-UNMAPPED-001";
    public static final String AMBIGUOUS_INGREDIENT_ID = "S2M2-NEG-AMBIGUOUS-001";

    public static final List<Fixture> ALL = List.of(
            missingDeclaration(),
            noActiveRuleSet(),
            unmappedIngredient(),
            ambiguousIngredient()
    );

    private NegativeGoldenFixtures() {
    }

    public static Fixture byId(String fixtureId) {
        return ALL.stream()
                .filter(fixture -> fixture.fixtureId().equals(fixtureId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown negative fixture: " + fixtureId));
    }

    private static Fixture missingDeclaration() {
        String productId = "prod_s2_m2_neg_missing_decl";
        String formulaVersionId = "formula_s2_m2_neg_missing_decl_v1";
        String labelVersionId = "label_s2_m2_neg_missing_decl_v1";
        String formulaItemId = "item_s2_m2_neg_missing_decl_v1";
        FormulaCompositionSnapshot formula = matchedSoyFormula(
                productId, formulaVersionId, formulaItemId);
        LabelValidationSnapshot label = label(
                labelVersionId, productId, formulaVersionId,
                MISSING_DECLARATION_RULE_SET_VERSION_ID,
                "Sunflower oil, soy lecithin.", List.of());
        AllergenDerivation derivation = soyDerivation(
                formulaVersionId, MISSING_DECLARATION_RULE_SET_VERSION_ID, formulaItemId,
                "ia_s2_m2_neg_soy_active_v1");
        ValidationFinding finding = new ValidationFinding(
                "rule_s2_m2_neg_soy_decl",
                "ALLERGEN_DECLARATION_MISSING",
                ValidationSeverity.ERROR,
                false,
                true,
                "SOY is derived but no CONTAINS declaration is present."
        );
        return new Fixture(
                MISSING_DECLARATION_ID,
                FailureFamily.MISSING_ALLERGEN_DECLARATION,
                formula,
                label,
                derivation,
                true,
                List.of(finding),
                ExpectedOutcome.completedFailure(finding.resultCode())
        );
    }

    private static Fixture noActiveRuleSet() {
        String productId = "prod_s2_m2_neg_no_active";
        String formulaVersionId = "formula_s2_m2_neg_no_active_v1";
        String labelVersionId = "label_s2_m2_neg_no_active_v1";
        String formulaItemId = "item_s2_m2_neg_no_active_v1";
        FormulaCompositionSnapshot formula = matchedSoyFormula(
                productId, formulaVersionId, formulaItemId);
        LabelValidationSnapshot label = label(
                labelVersionId, productId, formulaVersionId, INACTIVE_RULE_SET_VERSION_ID,
                "Sunflower oil, soy lecithin.",
                List.of(new LabelValidationSnapshot.AllergenDeclaration(
                        "all_s2_m2_neg_soy", "CONTAINS", "FORMULA_DERIVED", "Contains: Soy")));
        AllergenDerivation derivation = soyDerivation(
                formulaVersionId, INACTIVE_RULE_SET_VERSION_ID, formulaItemId,
                "ia_s2_m2_neg_soy_retired_v1");
        ApiError error = ApiError.of(
                "VALIDATION_PRECONDITION_FAILED",
                "The current label has no active rule-set available for evaluation."
        );
        return new Fixture(
                NO_ACTIVE_RULE_SET_ID,
                FailureFamily.NO_ACTIVE_RULE_SET,
                formula,
                label,
                derivation,
                false,
                List.of(),
                ExpectedOutcome.preconditionFailure(error)
        );
    }

    private static Fixture unmappedIngredient() {
        return unresolvedIngredient(
                UNMAPPED_INGREDIENT_ID,
                FailureFamily.UNMAPPED_INGREDIENT,
                "prod_s2_m2_neg_unmapped",
                "formula_s2_m2_neg_unmapped_v1",
                "label_s2_m2_neg_unmapped_v1",
                "item_s2_m2_neg_unmapped_v1",
                "mat_s2_m2_neg_unmapped",
                "spec_s2_m2_neg_unmapped_v1",
                "component_s2_m2_neg_unmapped_v1",
                "ing_s2_m2_neg_unmapped",
                "Mystery protein blend",
                "no canonical match",
                MatchStatus.UNMAPPED,
                UNMAPPED_RULE_SET_VERSION_ID,
                "rule_s2_m2_neg_unmapped",
                "INGREDIENT_UNMAPPED",
                "Ingredient phrase 'Mystery protein blend' is unmapped."
        );
    }

    private static Fixture ambiguousIngredient() {
        return unresolvedIngredient(
                AMBIGUOUS_INGREDIENT_ID,
                FailureFamily.AMBIGUOUS_INGREDIENT,
                "prod_s2_m2_neg_ambiguous",
                "formula_s2_m2_neg_ambiguous_v1",
                "label_s2_m2_neg_ambiguous_v1",
                "item_s2_m2_neg_ambiguous_v1",
                "mat_s2_m2_neg_ambiguous",
                "spec_s2_m2_neg_ambiguous_v1",
                "component_s2_m2_neg_ambiguous_v1",
                "ing_s2_m2_neg_ambiguous",
                "Natural flavor concentrate",
                "multiple canonical candidates",
                MatchStatus.AMBIGUOUS,
                AMBIGUOUS_RULE_SET_VERSION_ID,
                "rule_s2_m2_neg_ambiguous",
                "INGREDIENT_AMBIGUOUS",
                "Ingredient phrase 'Natural flavor concentrate' has multiple canonical matches."
        );
    }

    private static Fixture unresolvedIngredient(
            String fixtureId,
            FailureFamily family,
            String productId,
            String formulaVersionId,
            String labelVersionId,
            String formulaItemId,
            String supplierMaterialId,
            String specificationVersionId,
            String componentId,
            String ingredientId,
            String rawPhrase,
            String matchRule,
            MatchStatus matchStatus,
            String ruleSetVersionId,
            String ruleDefinitionId,
            String resultCode,
            String message
    ) {
        FormulaCompositionSnapshot.Component component = new FormulaCompositionSnapshot.Component(
                componentId, ingredientId, rawPhrase, matchRule, matchStatus);
        FormulaCompositionSnapshot formula = formula(
                productId, formulaVersionId, formulaItemId, supplierMaterialId,
                specificationVersionId, component);
        LabelValidationSnapshot label = label(
                labelVersionId, productId, formulaVersionId, ruleSetVersionId,
                rawPhrase + ".", List.of());
        AllergenDerivation.UnresolvedComponent unresolved =
                new AllergenDerivation.UnresolvedComponent(
                        formulaItemId, specificationVersionId, componentId, ingredientId,
                        rawPhrase, matchRule, matchStatus);
        AllergenDerivation derivation = new AllergenDerivation(
                formulaVersionId, ruleSetVersionId, JURISDICTION_CODE,
                List.of(), List.of(unresolved));
        ValidationFinding finding = new ValidationFinding(
                ruleDefinitionId, resultCode, ValidationSeverity.ERROR, false, true, message);
        return new Fixture(
                fixtureId,
                family,
                formula,
                label,
                derivation,
                true,
                List.of(finding),
                ExpectedOutcome.completedFailure(resultCode)
        );
    }

    private static FormulaCompositionSnapshot matchedSoyFormula(
            String productId,
            String formulaVersionId,
            String formulaItemId
    ) {
        return formula(
                productId,
                formulaVersionId,
                formulaItemId,
                "mat_s2_m2_neg_soy",
                "spec_s2_m2_neg_soy_v1",
                new FormulaCompositionSnapshot.Component(
                        "component_s2_m2_neg_soy_v1",
                        "ing_s2_m2_neg_soy_lecithin",
                        "Soy lecithin",
                        "exact canonical fixture match",
                        MatchStatus.MATCHED)
        );
    }

    private static FormulaCompositionSnapshot formula(
            String productId,
            String formulaVersionId,
            String formulaItemId,
            String supplierMaterialId,
            String specificationVersionId,
            FormulaCompositionSnapshot.Component component
    ) {
        return new FormulaCompositionSnapshot(
                productId,
                formulaVersionId,
                true,
                List.of(new FormulaCompositionSnapshot.Item(
                        formulaItemId, supplierMaterialId, specificationVersionId,
                        List.of(component)))
        );
    }

    private static LabelValidationSnapshot label(
            String labelVersionId,
            String productId,
            String formulaVersionId,
            String ruleSetVersionId,
            String rawIngredientText,
            List<LabelValidationSnapshot.AllergenDeclaration> declarations
    ) {
        return new LabelValidationSnapshot(
                labelVersionId,
                productId,
                formulaVersionId,
                ruleSetVersionId,
                JURISDICTION_CODE,
                rawIngredientText,
                true,
                PROVENANCE_ID,
                declarations
        );
    }

    private static AllergenDerivation soyDerivation(
            String formulaVersionId,
            String ruleSetVersionId,
            String formulaItemId,
            String ingredientAllergenId
    ) {
        AllergenFact fact = new AllergenFact(
                "all_s2_m2_neg_soy",
                "SOY",
                List.of(new AllergenFact.DerivationEvidence(
                        formulaItemId,
                        "spec_s2_m2_neg_soy_v1",
                        "component_s2_m2_neg_soy_v1",
                        "ing_s2_m2_neg_soy_lecithin",
                        ingredientAllergenId,
                        "fixture mapping: soy lecithin -> SOY",
                        PROVENANCE_ID))
        );
        return new AllergenDerivation(
                formulaVersionId,
                ruleSetVersionId,
                JURISDICTION_CODE,
                List.of(fact),
                List.of()
        );
    }

    public enum FailureFamily {
        MISSING_ALLERGEN_DECLARATION,
        NO_ACTIVE_RULE_SET,
        UNMAPPED_INGREDIENT,
        AMBIGUOUS_INGREDIENT
    }

    public record Fixture(
            String fixtureId,
            FailureFamily family,
            FormulaCompositionSnapshot formulaSnapshot,
            LabelValidationSnapshot labelSnapshot,
            AllergenDerivation expectedDerivation,
            boolean activeRuleSetExpected,
            List<ValidationFinding> expectedFindings,
            ExpectedOutcome expectedOutcome
    ) {
        public Fixture {
            fixtureId = required(fixtureId, "fixtureId");
            family = Objects.requireNonNull(family, "family");
            formulaSnapshot = Objects.requireNonNull(formulaSnapshot, "formulaSnapshot");
            labelSnapshot = Objects.requireNonNull(labelSnapshot, "labelSnapshot");
            expectedDerivation = Objects.requireNonNull(expectedDerivation, "expectedDerivation");
            expectedFindings = List.copyOf(Objects.requireNonNull(expectedFindings, "expectedFindings"));
            expectedOutcome = Objects.requireNonNull(expectedOutcome, "expectedOutcome");

            if (!formulaSnapshot.formulaVersionId().equals(labelSnapshot.formulaVersionId())
                    || !formulaSnapshot.formulaVersionId().equals(expectedDerivation.formulaVersionId())
                    || !labelSnapshot.ruleSetVersionId().equals(expectedDerivation.ruleSetVersionId())
                    || !labelSnapshot.jurisdictionCode().equals(expectedDerivation.jurisdictionCode())) {
                throw new IllegalArgumentException("Negative fixture input versions must agree");
            }
            if (expectedOutcome.completedRunStatus() == null) {
                if (!expectedFindings.isEmpty() || expectedOutcome.apiError() == null) {
                    throw new IllegalArgumentException("A precondition fixture has an ApiError and no findings");
                }
            } else {
                String expectedCode = expectedOutcome.code();
                if (expectedFindings.isEmpty() || expectedOutcome.apiError() != null
                        || expectedFindings.stream().noneMatch(finding ->
                        finding.resultCode().equals(expectedCode))) {
                    throw new IllegalArgumentException("A completed negative fixture requires its failed finding");
                }
            }
            if (expectedOutcome.passed() || !expectedOutcome.blocking()) {
                throw new IllegalArgumentException("A negative fixture cannot pass or be non-blocking");
            }
        }
    }

    public record ExpectedOutcome(
            boolean passed,
            boolean blocking,
            String code,
            int httpStatus,
            ValidationStatus completedRunStatus,
            ApiError apiError
    ) {
        public ExpectedOutcome {
            code = required(code, "code");
            if (passed || !blocking) {
                throw new IllegalArgumentException("Negative outcome must be failed and blocking");
            }
            if (completedRunStatus != null) {
                if (completedRunStatus != ValidationStatus.FAILED || httpStatus != 201 || apiError != null) {
                    throw new IllegalArgumentException("Completed negative runs are 201 FAILED without ApiError");
                }
            } else if (httpStatus != 422 || apiError == null || !code.equals(apiError.code())) {
                throw new IllegalArgumentException("Precondition failures are exact 422 ApiError outcomes");
            }
        }

        static ExpectedOutcome completedFailure(String resultCode) {
            return new ExpectedOutcome(
                    false, true, resultCode, 201, ValidationStatus.FAILED, null);
        }

        static ExpectedOutcome preconditionFailure(ApiError error) {
            return new ExpectedOutcome(
                    false, true, error.code(), 422, null, error);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
