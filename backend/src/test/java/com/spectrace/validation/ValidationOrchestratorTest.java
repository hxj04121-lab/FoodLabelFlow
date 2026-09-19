package com.spectrace.validation;

import com.spectrace.allergen.application.port.AllergenDerivation;
import com.spectrace.allergen.application.port.AllergenFact;
import com.spectrace.allergen.application.port.AllergenFactsPort;
import com.spectrace.catalog.application.port.FormulaCompositionPort;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot;
import com.spectrace.label.application.port.LabelSnapshotPort;
import com.spectrace.label.application.port.LabelValidationSnapshot;
import com.spectrace.validation.application.ValidationFailure;
import com.spectrace.validation.application.ValidationOrchestrator;
import com.spectrace.validation.application.ValidationEvaluation;
import com.spectrace.validation.application.port.RuleSetVersionRepository;
import com.spectrace.validation.application.port.ValidationIntegration;
import com.spectrace.validation.application.rule.IngredientToAllergenEvaluator;
import com.spectrace.validation.application.rule.LabelDeclarationEvaluator;
import com.spectrace.validation.application.rule.RuleEvaluator;
import com.spectrace.validation.application.rule.RuleEvaluatorRegistry;
import com.spectrace.validation.domain.RuleDefinition;
import com.spectrace.validation.domain.RuleSetLifecycleStatus;
import com.spectrace.validation.domain.RuleSetVersion;
import com.spectrace.validation.domain.RuleType;
import com.spectrace.validation.domain.ValidationFinding;
import com.spectrace.validation.domain.ValidationSeverity;
import com.spectrace.validation.domain.ValidationStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationOrchestratorTest {
    private static final String LABEL_ID = "label-1";
    private static final String PRODUCT_ID = "product-1";
    private static final String FORMULA_ID = "formula-1";
    private static final String RULE_SET_ID = "rules-1";
    private static final Clock TEST_CLOCK = Clock.fixed(
            Instant.parse("2026-09-16T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void evaluatesBothRuleTypesInStableDefinitionOrderAndReturnsTraceableFindings() {
        var integration = new RecordingIntegration();
        var label = label(true, List.of(declaration("all_soy", "CONTAINS")));
        var formula = formula(FormulaCompositionSnapshot.MatchStatus.MATCHED);
        var ruleSet = activeRuleSet(ingredientRule(), labelRule());
        var result = orchestrator(label, formula, ruleSet, derivation(List.of(fact("all_soy", "SOY")), List.of()),
                allEvaluators(), integration).orchestrate(LABEL_ID, RULE_SET_ID);

        assertThat(result.status()).isEqualTo(ValidationStatus.PASSED);
        assertThat(result.findings()).extracting(ValidationFinding::ruleDefinitionId)
                .containsExactly("rule-ingredient", "rule-label");
        assertThat(result.findings()).extracting(ValidationFinding::resultCode)
                .containsExactly("SOY_DECLARATION_PRESENT", "LABEL_DECLARATION_PRESENT");
        assertThat(integration.permission).isEqualTo(ValidationOrchestrator.VALIDATE_PERMISSION);
        assertThat(result.actorId()).isEqualTo("actor-1");
    }

    @Test
    void evaluatesAllRulesAndFailsWhenAFormulaAllergenIsNotDeclared() {
        var label = label(true, List.of());
        var formula = formula(FormulaCompositionSnapshot.MatchStatus.MATCHED);
        var ruleSet = activeRuleSet(ingredientRule(), labelRule());
        var result = orchestrator(label, formula, ruleSet, derivation(List.of(fact("all_soy", "SOY")), List.of()),
                allEvaluators(), new RecordingIntegration()).orchestrate(LABEL_ID, RULE_SET_ID);

        assertThat(result.status()).isEqualTo(ValidationStatus.FAILED);
        assertThat(result.findings()).extracting(ValidationFinding::resultCode)
                .containsExactly("ALLERGEN_DECLARATION_MISSING", "ALLERGEN_DECLARATION_MISSING");
        assertThat(result.findings()).allSatisfy(finding -> assertThat(finding.blocking()).isTrue());
    }

    @Test
    void preservesEveryUnresolvedComponentAsAnInputFinding() {
        var label = label(true, List.of(declaration("all_soy", "CONTAINS")));
        var formula = formula(FormulaCompositionSnapshot.MatchStatus.UNMAPPED);
        var unresolved = new AllergenDerivation.UnresolvedComponent(
                "item-1", "spec-1", "component-1", "ingredient-1",
                "Unknown phrase", "no unique match", FormulaCompositionSnapshot.MatchStatus.UNMAPPED);
        var ruleSet = activeRuleSet(ingredientRule(), labelRule());
        var result = orchestrator(label, formula, ruleSet, derivation(List.of(), List.of(unresolved)),
                allEvaluators(), new RecordingIntegration()).orchestrate(LABEL_ID, RULE_SET_ID);

        assertThat(result.status()).isEqualTo(ValidationStatus.FAILED);
        assertThat(result.findings().getFirst().ruleDefinitionId()).isNull();
        assertThat(result.findings().getFirst().resultCode()).isEqualTo("FORMULA_COMPONENT_UNRESOLVED");
        assertThat(result.findings()).extracting(ValidationFinding::ruleDefinitionId)
                .containsExactly(null, "rule-ingredient", "rule-label");
    }

    @Test
    void mapsMissingAndStaleLabelsToDistinctBoundaryFailures() {
        var currentLabel = label(true, List.of(declaration("all_soy", "CONTAINS")));
        var formula = formula(FormulaCompositionSnapshot.MatchStatus.MATCHED);
        var ruleSet = activeRuleSet(ingredientRule(), labelRule());
        var integration = new RecordingIntegration();

        assertThatThrownBy(() -> orchestrator(currentLabel, formula, ruleSet,
                derivation(List.of(fact("all_soy", "SOY")), List.of()), allEvaluators(), integration)
                .orchestrate("missing", RULE_SET_ID))
                .isInstanceOfSatisfying(ValidationFailure.class, failure -> {
                    assertThat(failure.status()).isEqualTo(404);
                    assertThat(failure.code()).isEqualTo("RESOURCE_NOT_FOUND");
                });

        assertThatThrownBy(() -> orchestrator(label(false, List.of()), formula, ruleSet,
                derivation(List.of(fact("all_soy", "SOY")), List.of()), allEvaluators(), integration)
                .orchestrate(LABEL_ID, RULE_SET_ID))
                .isInstanceOfSatisfying(ValidationFailure.class, failure -> {
                    assertThat(failure.status()).isEqualTo(409);
                    assertThat(failure.code()).isEqualTo("LABEL_VERSION_NOT_CURRENT");
                });
    }

    @Test
    void distinguishesAStaleFormulaFromIncompleteCompositionEvenWhenLabelIsCurrent() {
        var currentLabel = label(true, List.of());
        var currentFormula = formula(FormulaCompositionSnapshot.MatchStatus.MATCHED);
        var staleFormula = new FormulaCompositionSnapshot(
                PRODUCT_ID, FORMULA_ID, false, currentFormula.items());
        var incompleteStaleFormula = new FormulaCompositionSnapshot(PRODUCT_ID, FORMULA_ID, false, List.of());
        var incompleteFormula = new FormulaCompositionSnapshot(PRODUCT_ID, FORMULA_ID, true, List.of());

        for (var formula : List.of(staleFormula, incompleteStaleFormula)) {
            assertThatThrownBy(() -> orchestrator(currentLabel, formula, activeRuleSet(ingredientRule()),
                    derivation(List.of(), List.of()), allEvaluators(), new RecordingIntegration())
                    .orchestrate(LABEL_ID, RULE_SET_ID))
                    .isInstanceOfSatisfying(ValidationFailure.class, failure -> {
                        assertThat(failure.status()).isEqualTo(409);
                        assertThat(failure.code()).isEqualTo("LABEL_VERSION_NOT_CURRENT");
                    });
        }
        assertThatThrownBy(() -> orchestrator(currentLabel, incompleteFormula, activeRuleSet(ingredientRule()),
                derivation(List.of(), List.of()), allEvaluators(), new RecordingIntegration())
                .orchestrate(LABEL_ID, RULE_SET_ID))
                .isInstanceOfSatisfying(ValidationFailure.class, failure -> {
                    assertThat(failure.status()).isEqualTo(422);
                    assertThat(failure.code()).isEqualTo("VALIDATION_PRECONDITION_FAILED");
                });
    }

    @Test
    void rejectsRuleSetMismatchAndNoActiveDefinitionsBeforeEvaluation() {
        var label = label(true, List.of(declaration("all_soy", "CONTAINS")));
        var formula = formula(FormulaCompositionSnapshot.MatchStatus.MATCHED);
        var integration = new RecordingIntegration();
        var rules = activeRuleSet(ingredientRule(), labelRule());

        assertThatThrownBy(() -> orchestrator(label, formula, rules,
                derivation(List.of(fact("all_soy", "SOY")), List.of()), allEvaluators(), integration)
                .orchestrate(LABEL_ID, "rules-2"))
                .isInstanceOfSatisfying(ValidationFailure.class, failure -> {
                    assertThat(failure.status()).isEqualTo(422);
                    assertThat(failure.code()).isEqualTo("VALIDATION_PRECONDITION_FAILED");
                });

        assertThatThrownBy(() -> orchestrator(label, formula,
                activeRuleSet(), derivation(List.of(fact("all_soy", "SOY")), List.of()),
                allEvaluators(), integration).orchestrate(LABEL_ID, RULE_SET_ID))
                .isInstanceOfSatisfying(ValidationFailure.class, failure -> {
                    assertThat(failure.status()).isEqualTo(422);
                    assertThat(failure.getMessage()).contains("no active definitions");
                });
    }

    @Test
    void rejectsAnIneffectiveRuleSetAndFailsClosedWhenAnEvaluatorIsMissing() {
        var label = label(true, List.of(declaration("all_soy", "CONTAINS")));
        var formula = formula(FormulaCompositionSnapshot.MatchStatus.MATCHED);
        var facts = derivation(List.of(fact("all_soy", "SOY")), List.of());
        var expired = new RuleSetVersion(
                RULE_SET_ID, "TEST_RULES", "1", "US", RuleSetLifecycleStatus.ACTIVE,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 15), true,
                "Expired rules", "prov-rules", List.of(ingredientRule(), labelRule()));

        assertThatThrownBy(() -> orchestrator(label, formula, expired, facts,
                allEvaluators(), new RecordingIntegration()).orchestrate(LABEL_ID, RULE_SET_ID))
                .isInstanceOfSatisfying(ValidationFailure.class, failure -> {
                    assertThat(failure.status()).isEqualTo(422);
                    assertThat(failure.getMessage()).contains("not effective");
                });

        var recording = new RecordingEvaluator(RuleType.INGREDIENT_TO_ALLERGEN);
        assertThatThrownBy(() -> orchestrator(label, formula,
                activeRuleSet(ingredientRule(), labelRule()), facts,
                new RuleEvaluatorRegistry(List.of(recording)), new RecordingIntegration())
                .orchestrate(LABEL_ID, RULE_SET_ID))
                .isInstanceOfSatisfying(ValidationFailure.class, failure -> {
                    assertThat(failure.status()).isEqualTo(500);
                    assertThat(failure.code()).isEqualTo("INTERNAL_ERROR");
                });
        assertThat(recording.invocations).isZero();
    }

    private static ValidationOrchestrator orchestrator(
            LabelValidationSnapshot label,
            FormulaCompositionSnapshot formula,
            RuleSetVersion ruleSet,
            AllergenDerivation derivation,
            RuleEvaluatorRegistry evaluators,
            RecordingIntegration integration
    ) {
        LabelSnapshotPort labels = id -> id.equals(label.labelVersionId())
                ? Optional.of(label) : Optional.empty();
        FormulaCompositionPort formulas = id -> id.equals(formula.formulaVersionId())
                ? Optional.of(formula) : Optional.empty();
        AllergenFactsPort facts = new AllergenFactsPort() {
            @Override
            public List<com.spectrace.allergen.application.port.AllergenEntry> listAllergens(String jurisdictionCode) {
                return List.of();
            }

            @Override
            public AllergenDerivation derive(
                    FormulaCompositionSnapshot input, String ruleSetVersionId, String jurisdictionCode) {
                assertThat(input).isEqualTo(formula);
                return derivation;
            }
        };
        RuleSetVersionRepository ruleSets = new RuleSetVersionRepository() {
            @Override
            public Optional<RuleSetVersion> findById(String ruleSetVersionId) {
                return ruleSetVersionId.equals(ruleSet.ruleSetVersionId())
                        ? Optional.of(ruleSet) : Optional.empty();
            }

            @Override
            public Optional<RuleSetVersion> findActiveById(String ruleSetVersionId) {
                return findById(ruleSetVersionId);
            }
        };
        return new ValidationOrchestrator(
                labels, formulas, facts, ruleSets, evaluators, integration, TEST_CLOCK);
    }

    private static RuleEvaluatorRegistry allEvaluators() {
        return new RuleEvaluatorRegistry(List.of(
                new IngredientToAllergenEvaluator(), new LabelDeclarationEvaluator()));
    }

    private static LabelValidationSnapshot label(
            boolean current, List<LabelValidationSnapshot.AllergenDeclaration> declarations) {
        return new LabelValidationSnapshot(
                LABEL_ID, PRODUCT_ID, FORMULA_ID, RULE_SET_ID, "US", "Soy lecithin",
                current, "prov-label", declarations);
    }

    private static FormulaCompositionSnapshot formula(FormulaCompositionSnapshot.MatchStatus status) {
        return new FormulaCompositionSnapshot(
                PRODUCT_ID, FORMULA_ID, true,
                List.of(new FormulaCompositionSnapshot.Item(
                        "item-1", "material-1", "spec-1", List.of(
                        new FormulaCompositionSnapshot.Component(
                                "component-1", "ingredient-1", "Soy lecithin",
                                "fixture match", status)))));
    }

    private static AllergenDerivation derivation(
            List<AllergenFact> facts, List<AllergenDerivation.UnresolvedComponent> unresolved) {
        return new AllergenDerivation(FORMULA_ID, RULE_SET_ID, "US", facts, unresolved);
    }

    private static RuleSetVersion activeRuleSet(RuleDefinition... definitions) {
        return new RuleSetVersion(
                RULE_SET_ID, "TEST_RULES", "1", "US", RuleSetLifecycleStatus.ACTIVE,
                LocalDate.of(2026, 1, 1), null, true, "Test rules", "prov-rules", List.of(definitions));
    }

    private static RuleDefinition ingredientRule() {
        return new RuleDefinition(
                "rule-ingredient", RULE_SET_ID, "INGREDIENT_SOY", RuleType.INGREDIENT_TO_ALLERGEN,
                "all_soy", "soy", ValidationSeverity.ERROR, true, "Soy declaration");
    }

    private static RuleDefinition labelRule() {
        return new RuleDefinition(
                "rule-label", RULE_SET_ID, "LABEL_CONTAINS_DECLARATION",
                RuleType.LABEL_DECLARATION_VALIDATION, null,
                "label declaration type must be CONTAINS for MVP", ValidationSeverity.ERROR, true,
                "Declaration shape");
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

    private static final class RecordingIntegration implements ValidationIntegration {
        private String permission;

        @Override
        public String requireActor(String permission) {
            this.permission = permission;
            return "actor-1";
        }

        @Override
        public void auditValidation(
                String actorId, String labelVersionId, String ruleSetVersionId,
                String validationRunId, String dataProvenanceId) {
            throw new AssertionError("SCRUM-43 must not write audit events");
        }
    }

    private static final class RecordingEvaluator implements RuleEvaluator {
        private final RuleType type;
        private int invocations;

        private RecordingEvaluator(RuleType type) {
            this.type = type;
        }

        @Override
        public RuleType ruleType() {
            return type;
        }

        @Override
        public List<ValidationFinding> evaluate(
                RuleDefinition rule, com.spectrace.validation.application.rule.RuleEvaluationContext context) {
            invocations++;
            return List.of(ValidationFinding.forRule(rule, "RECORDED", true, "recorded"));
        }
    }
}
