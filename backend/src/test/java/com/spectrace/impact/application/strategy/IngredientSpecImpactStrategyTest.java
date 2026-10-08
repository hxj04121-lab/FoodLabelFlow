package com.spectrace.impact.application.strategy;

import com.spectrace.allergen.application.port.AllergenDerivation;
import com.spectrace.allergen.application.port.AllergenEntry;
import com.spectrace.allergen.application.port.AllergenFact;
import com.spectrace.allergen.application.port.AllergenFactsPort;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot.Component;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot.Item;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot.MatchStatus;
import com.spectrace.impact.application.ImpactFailure;
import com.spectrace.impact.application.RelevantProductTarget;
import com.spectrace.impact.domain.ChangeRequest;
import com.spectrace.impact.domain.ChangeRequest.VersionChange;
import com.spectrace.impact.domain.ChangeRequestStatus;
import com.spectrace.impact.domain.ChangeType;
import com.spectrace.impact.domain.ImpactClassification;
import com.spectrace.impact.domain.ImpactFinding;
import com.spectrace.label.application.port.LabelValidationSnapshot;
import com.spectrace.label.application.port.LabelValidationSnapshot.AllergenDeclaration;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngredientSpecImpactStrategyTest {
    private static final String RULE_SET = "ruleset_us_falcpa_demo_v1";
    private static final String SPEC_V1 = "spec_chocolate_v1";
    private static final String SPEC_V2 = "spec_chocolate_v2";
    private static final String N = "formula_a_v1";
    private static final String N_PLUS_1 = "formula_a_v2";
    private static final String LABEL = "label_a_v1";
    private static final String CHOCOLATE_ITEM = "fi_a_v2_1";
    /** ingredient → allergen mapping used by the fake derivation. */
    private static final Map<String, String[]> ALLERGENS = Map.of(
            "ing_soy_lecithin", new String[]{"alg_soy", "SOY"},
            "ing_wheat_flour", new String[]{"alg_wheat", "WHEAT"},
            "ing_milk_powder", new String[]{"alg_milk", "MILK"});

    private final Map<String, FormulaCompositionSnapshot> formulas = new HashMap<>();
    private final Map<String, LabelValidationSnapshot> labels = new HashMap<>();
    private final FakeAllergens allergens = new FakeAllergens();
    private final IngredientSpecImpactStrategy strategy = new IngredientSpecImpactStrategy(
            id -> Optional.ofNullable(formulas.get(id)), id -> Optional.ofNullable(labels.get(id)), allergens);

    @Test
    void handlesIngredientSpecChangesOnly() {
        assertThat(strategy.changeType()).isEqualTo(ChangeType.INGREDIENT_SPEC);
        adopted(chocolateV2WithSoy());
        label(N, "alg_soy");

        assertThatIllegalArgumentException().isThrownBy(() ->
                strategy.assess(change(ChangeType.FORMULA), target(), RULE_SET));
    }

    @Test
    void everyDerivedAllergenAlreadyDeclaredIsNoAction() {
        adopted(chocolateV2WithSoy(), item("fi_a_v2_2", "mat_wheat_flour", "spec_wheat_flour_v1", "ing_wheat_flour"));
        label(N, "alg_wheat", "alg_soy");

        ProductImpactAssessment assessment = strategy.assess(change(ChangeType.INGREDIENT_SPEC), target(), RULE_SET);

        assertThat(assessment.classification()).isEqualTo(ImpactClassification.NO_ACTION);
        assertThat(assessment.missingAllergenCodes()).isEmpty();
        assertThat(assessment.productId()).isEqualTo("prod_a");
        assertThat(assessment.currentFormulaVersionId()).isEqualTo(N);
        assertThat(assessment.proposedFormulaVersionId()).isEqualTo(N_PLUS_1);
        assertThat(assessment.currentLabelVersionId()).isEqualTo(LABEL);
        assertThat(assessment.explanation()).isEqualTo("Formula formula_a_v2 with specification spec_chocolate_v2 "
                + "derives SOY, WHEAT, all already declared as CONTAINS on published label label_a_v1.");
        assertThat(allergens.requests).containsExactly(N_PLUS_1 + "|" + RULE_SET + "|US");
    }

    @Test
    void aDerivedAllergenTheLabelDoesNotDeclareIsReviewRequiredWithItsCode() {
        adopted(chocolateV2WithSoy(), item("fi_a_v2_2", "mat_wheat_flour", "spec_wheat_flour_v1", "ing_wheat_flour"));
        label(N, "alg_wheat");

        ProductImpactAssessment assessment = strategy.assess(change(ChangeType.INGREDIENT_SPEC), target(), RULE_SET);

        assertThat(assessment.classification()).isEqualTo(ImpactClassification.REVIEW_REQUIRED);
        assertThat(assessment.missingAllergenCodes()).containsExactly("SOY");
        assertThat(assessment.explanation()).isEqualTo("Formula formula_a_v2 with specification spec_chocolate_v2 "
                + "derives SOY, not declared as CONTAINS on published label label_a_v1.");

        ImpactFinding finding = assessment.toFinding("finding-1", "run-1", "prov_derived");
        assertThat(finding.classification()).isEqualTo(ImpactClassification.REVIEW_REQUIRED);
        assertThat(finding.missingAllergenCodes()).containsExactly("SOY");
        assertThat(finding.currentFormulaVersionId()).isEqualTo(N);
        assertThat(finding.proposedFormulaVersionId()).isEqualTo(N_PLUS_1);
        assertThat(finding.requiresReviewTask()).isTrue();
    }

    @Test
    void everyMissingCodeIsReportedSortedAndOnlyContainsDeclarationsCount() {
        adopted(chocolateV2WithSoy(),
                item("fi_a_v2_2", "mat_wheat_flour", "spec_wheat_flour_v1", "ing_wheat_flour"),
                item("fi_a_v2_3", "mat_milk_powder", "spec_milk_powder_v1", "ing_milk_powder"));
        labels.put(LABEL, snapshot(N, List.of(
                new AllergenDeclaration("alg_wheat", "MAY_CONTAIN", "MANUAL", "May contain wheat"),
                new AllergenDeclaration("alg_milk", "CONTAINS", "MANUAL", "Milk"))));

        assertThat(strategy.assess(change(ChangeType.INGREDIENT_SPEC), target(), RULE_SET).missingAllergenCodes())
                .containsExactly("SOY", "WHEAT");
    }

    @Test
    void declarationsAreMatchedByAllergenIdNotByDisplayText() {
        adopted(chocolateV2WithSoy());
        labels.put(LABEL, snapshot(N, List.of(new AllergenDeclaration("alg_soy_other", "CONTAINS", "MANUAL", "SOY"))));

        assertThat(strategy.assess(change(ChangeType.INGREDIENT_SPEC), target(), RULE_SET).missingAllergenCodes())
                .containsExactly("SOY");
    }

    @Test
    void aCompleteDerivationWithNoAllergensIsACompletedNegativeResult() {
        adopted(item(CHOCOLATE_ITEM, "mat_chocolate_base", SPEC_V2, "ing_cocoa", "ing_sugar"));
        label(N);

        ProductImpactAssessment assessment = strategy.assess(change(ChangeType.INGREDIENT_SPEC), target(), RULE_SET);

        assertThat(assessment.classification()).isEqualTo(ImpactClassification.NO_ACTION);
        assertThat(assessment.explanation()).contains("derives no allergens from fully matched components");
    }

    @Test
    void anEmptyDerivationWithUnresolvedComponentsIsNeverNoAction() {
        adopted(new Item(CHOCOLATE_ITEM, "mat_chocolate_base", SPEC_V2, List.of(
                component("sc_cocoa", "ing_cocoa", MatchStatus.MATCHED),
                component("sc_flavour", "ing_unknown", MatchStatus.AMBIGUOUS))));
        label(N);

        assertThatIllegalStateException()
                .isThrownBy(() -> strategy.assess(change(ChangeType.INGREDIENT_SPEC), target(), RULE_SET))
                .withMessageContaining("1 unresolved or empty specification input(s)");
    }

    @Test
    void anItemWithoutComponentsCannotProveThatNothingIsMissing() {
        adopted(chocolateV2WithSoy(), new Item("fi_a_v2_2", "mat_neutral_base", "spec_neutral_base_v1", List.of()));
        label(N, "alg_soy");

        assertThatIllegalStateException()
                .isThrownBy(() -> strategy.assess(change(ChangeType.INGREDIENT_SPEC), target(), RULE_SET));
    }

    @Test
    void anIncompleteDerivationThatStillProvesAMissingAllergenIsReviewRequired() {
        adopted(chocolateV2WithSoy(), new Item("fi_a_v2_2", "mat_neutral_base", "spec_neutral_base_v1", List.of(
                component("sc_neutral", "ing_neutral_base", MatchStatus.UNMAPPED))));
        label(N);

        ProductImpactAssessment assessment = strategy.assess(change(ChangeType.INGREDIENT_SPEC), target(), RULE_SET);

        assertThat(assessment.missingAllergenCodes()).containsExactly("SOY");
        assertThat(assessment.explanation()).endsWith(" 1 unresolved or empty specification input(s) also need review.");
    }

    @Test
    void aCurrentFormulaStillOnThePreviousSpecificationIsAdoptionPending() {
        // No N+1 yet: the current formula is still N, behind the published label.
        formulas.put(N, formula(N, true, item(CHOCOLATE_ITEM, "mat_chocolate_base", SPEC_V1, "ing_cocoa")));
        label(N);

        assertFailure(() -> strategy.assess(change(ChangeType.INGREDIENT_SPEC),
                new RelevantProductTarget("prod_a", N, LABEL, List.of(CHOCOLATE_ITEM)), RULE_SET),
                422, "FORMULA_ADOPTION_PENDING");
        assertThat(allergens.requests).isEmpty();
    }

    @Test
    void aNewerFormulaThatDidNotAdoptTheTargetSpecificationIsAdoptionPending() {
        adopted(item(CHOCOLATE_ITEM, "mat_chocolate_base", SPEC_V1, "ing_cocoa"));
        label(N);

        assertFailure(() -> strategy.assess(change(ChangeType.INGREDIENT_SPEC), target(), RULE_SET),
                422, "FORMULA_ADOPTION_PENDING");
    }

    @Test
    void aLabelAlreadyBoundToTheAdoptedFormulaIsAConflictNotAFinding() {
        adopted(chocolateV2WithSoy());
        label(N_PLUS_1, "alg_soy");

        assertFailure(() -> strategy.assess(change(ChangeType.INGREDIENT_SPEC), target(), RULE_SET),
                409, "DATA_CONFLICT");
    }

    @Test
    void inconsistentReadsAreServerFaultsNeverAClassification() {
        adopted(chocolateV2WithSoy());

        // Label gone, or belonging to another product.
        assertThatIllegalStateException().isThrownBy(() ->
                strategy.assess(change(ChangeType.INGREDIENT_SPEC), target(), RULE_SET));
        labels.put(LABEL, new LabelValidationSnapshot(LABEL, "prod_other", N, RULE_SET, "US", "", false,
                "prov_project_seed", List.of()));
        assertThatIllegalStateException().isThrownBy(() ->
                strategy.assess(change(ChangeType.INGREDIENT_SPEC), target(), RULE_SET));

        // Current formula no longer current, or missing the item that made the product relevant.
        label(N, "alg_soy");
        formulas.put(N_PLUS_1, formula(N_PLUS_1, false, chocolateV2WithSoy()));
        assertThatIllegalStateException().isThrownBy(() ->
                strategy.assess(change(ChangeType.INGREDIENT_SPEC), target(), RULE_SET));
        adopted(item("fi_a_v2_9", "mat_chocolate_base", SPEC_V2, "ing_cocoa"));
        assertThatIllegalStateException().isThrownBy(() ->
                strategy.assess(change(ChangeType.INGREDIENT_SPEC), target(), RULE_SET));

        // A derivation for other versions.
        adopted(chocolateV2WithSoy());
        allergens.ruleSetOverride = "ruleset_other";
        assertThatIllegalStateException().isThrownBy(() ->
                strategy.assess(change(ChangeType.INGREDIENT_SPEC), target(), RULE_SET));
    }

    @Test
    void anAssessmentDerivesItsClassificationFromTheMissingCodes() {
        var noAction = new ProductImpactAssessment("p", "f1", "f2", "l1", List.of(), "ok");
        var review = new ProductImpactAssessment("p", "f1", "f2", "l1", List.of("SOY"), "missing");

        assertThat(noAction.classification()).isEqualTo(ImpactClassification.NO_ACTION);
        assertThat(review.classification()).isEqualTo(ImpactClassification.REVIEW_REQUIRED);
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ProductImpactAssessment("p", "f1", null, "l1", List.of(), "ok"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ProductImpactAssessment("p", "f1", "f1", "l1", List.of(), "ok").toFinding("i", "r", "prov"));
    }

    private void adopted(Item... items) {
        formulas.put(N_PLUS_1, formula(N_PLUS_1, true, items));
    }

    private void label(String formulaVersionId, String... declaredAllergenIds) {
        labels.put(LABEL, snapshot(formulaVersionId, Arrays.stream(declaredAllergenIds)
                .map(id -> new AllergenDeclaration(id, "CONTAINS", "MANUAL", id)).toList()));
    }

    private static LabelValidationSnapshot snapshot(String formulaVersionId, List<AllergenDeclaration> declarations) {
        // isCurrent is false after adoption: the label's formula N is no longer current.
        return new LabelValidationSnapshot(LABEL, "prod_a", formulaVersionId, RULE_SET, "US", "Ingredients",
                false, "prov_project_seed", declarations);
    }

    private static FormulaCompositionSnapshot formula(String formulaVersionId, boolean current, Item... items) {
        return new FormulaCompositionSnapshot("prod_a", formulaVersionId, current, List.of(items));
    }

    private static Item chocolateV2WithSoy() {
        return item(CHOCOLATE_ITEM, "mat_chocolate_base", SPEC_V2, "ing_cocoa", "ing_sugar", "ing_soy_lecithin");
    }

    private static Item item(String id, String material, String specification, String... ingredientIds) {
        return new Item(id, material, specification, Arrays.stream(ingredientIds)
                .map(ingredient -> component("sc_" + id + "_" + ingredient, ingredient, MatchStatus.MATCHED))
                .toList());
    }

    private static Component component(String id, String ingredientId, MatchStatus status) {
        return new Component(id, ingredientId, ingredientId, "Project fixture component", status);
    }

    private static RelevantProductTarget target() {
        return new RelevantProductTarget("prod_a", N_PLUS_1, LABEL, List.of(CHOCOLATE_ITEM));
    }

    private static ChangeRequest change(ChangeType type) {
        return new ChangeRequest("cr-1", "CR-cr-1", type, ChangeRequestStatus.SUBMITTED,
                Instant.parse("2026-10-01T00:00:00Z"), "user_change_manager", "Chocolate Base Spec V2 adds Soy Lecithin",
                new VersionChange(SPEC_V1, SPEC_V2), "prov_scenario_input");
    }

    private static void assertFailure(ThrowingCallable call, int status, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ImpactFailure.class, failure -> {
            assertThat(failure.status()).isEqualTo(status);
            assertThat(failure.code()).isEqualTo(code);
        });
    }

    /** Mirrors the Sprint 2 derivation contract: MATCHED rows map, other rows stay unresolved. */
    private static final class FakeAllergens implements AllergenFactsPort {
        private final List<String> requests = new ArrayList<>();
        private String ruleSetOverride;

        @Override
        public List<AllergenEntry> listAllergens(String jurisdictionCode) {
            throw new UnsupportedOperationException("Impact classification does not list the catalogue");
        }

        @Override
        public AllergenDerivation derive(
                FormulaCompositionSnapshot formula, String ruleSetVersionId, String jurisdictionCode) {
            requests.add(formula.formulaVersionId() + "|" + ruleSetVersionId + "|" + jurisdictionCode);
            Map<String, List<AllergenFact.DerivationEvidence>> evidence = new HashMap<>();
            Map<String, String> codes = new HashMap<>();
            List<AllergenDerivation.UnresolvedComponent> unresolved = new ArrayList<>();
            for (Item item : formula.items()) {
                for (Component component : item.components()) {
                    if (component.matchStatus() != MatchStatus.MATCHED) {
                        unresolved.add(new AllergenDerivation.UnresolvedComponent(item.formulaItemId(),
                                item.specificationVersionId(), component.specComponentId(), component.ingredientId(),
                                component.rawPhrase(), component.matchRule(), component.matchStatus()));
                        continue;
                    }
                    String[] allergen = ALLERGENS.get(component.ingredientId());
                    if (allergen != null) {
                        codes.put(allergen[0], allergen[1]);
                        evidence.computeIfAbsent(allergen[0], ignored -> new ArrayList<>())
                                .add(new AllergenFact.DerivationEvidence(item.formulaItemId(),
                                        item.specificationVersionId(), component.specComponentId(),
                                        component.ingredientId(), "ia_" + component.ingredientId(),
                                        "INGREDIENT_TO_ALLERGEN", "prov_project_seed"));
                    }
                }
            }
            List<AllergenFact> facts = evidence.entrySet().stream()
                    .map(entry -> new AllergenFact(entry.getKey(), codes.get(entry.getKey()), entry.getValue()))
                    .toList();
            return new AllergenDerivation(formula.formulaVersionId(),
                    ruleSetOverride == null ? ruleSetVersionId : ruleSetOverride, jurisdictionCode, facts, unresolved);
        }
    }
}
