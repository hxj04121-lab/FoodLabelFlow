package com.spectrace.validation.application;

import com.spectrace.allergen.application.port.AllergenDerivation;
import com.spectrace.allergen.application.port.AllergenFactsPort;
import com.spectrace.catalog.application.port.FormulaCompositionPort;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot;
import com.spectrace.label.application.port.LabelSnapshotPort;
import com.spectrace.label.application.port.LabelValidationSnapshot;
import com.spectrace.validation.application.port.RuleSetVersionRepository;
import com.spectrace.validation.application.port.ValidationIntegration;
import com.spectrace.validation.application.rule.RuleEvaluationContext;
import com.spectrace.validation.application.rule.RuleEvaluator;
import com.spectrace.validation.application.rule.RuleEvaluatorRegistry;
import com.spectrace.validation.domain.RuleDefinition;
import com.spectrace.validation.domain.RuleSetLifecycleStatus;
import com.spectrace.validation.domain.RuleSetVersion;
import com.spectrace.validation.domain.RuleType;
import com.spectrace.validation.domain.ValidationFinding;
import com.spectrace.validation.domain.ValidationSeverity;
import com.spectrace.validation.domain.ValidationStatus;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Synchronous validation application flow. It evaluates a fully captured input
 * and leaves run/result/audit persistence to SCRUM-44.
 */
public class ValidationOrchestrator {
    public static final String VALIDATE_PERMISSION = "LABEL.VALIDATE";

    private final LabelSnapshotPort labelSnapshots;
    private final FormulaCompositionPort formulaCompositions;
    private final AllergenFactsPort allergenFacts;
    private final RuleSetVersionRepository ruleSets;
    private final RuleEvaluatorRegistry evaluators;
    private final ValidationIntegration validationIntegration;
    private final Clock clock;

    public ValidationOrchestrator(
            LabelSnapshotPort labelSnapshots,
            FormulaCompositionPort formulaCompositions,
            AllergenFactsPort allergenFacts,
            RuleSetVersionRepository ruleSets,
            RuleEvaluatorRegistry evaluators,
            ValidationIntegration validationIntegration
    ) {
        this(labelSnapshots, formulaCompositions, allergenFacts, ruleSets, evaluators,
                validationIntegration, Clock.systemUTC());
    }

    public ValidationOrchestrator(
            LabelSnapshotPort labelSnapshots,
            FormulaCompositionPort formulaCompositions,
            AllergenFactsPort allergenFacts,
            RuleSetVersionRepository ruleSets,
            RuleEvaluatorRegistry evaluators,
            ValidationIntegration validationIntegration,
            Clock clock
    ) {
        this.labelSnapshots = Objects.requireNonNull(labelSnapshots, "labelSnapshots");
        this.formulaCompositions = Objects.requireNonNull(formulaCompositions, "formulaCompositions");
        this.allergenFacts = Objects.requireNonNull(allergenFacts, "allergenFacts");
        this.ruleSets = Objects.requireNonNull(ruleSets, "ruleSets");
        this.evaluators = Objects.requireNonNull(evaluators, "evaluators");
        this.validationIntegration = Objects.requireNonNull(validationIntegration, "validationIntegration");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Evaluate one label against the requested active rule-set version. */
    public ValidationEvaluation orchestrate(String labelVersionId, String ruleSetVersionId) {
        String requestedLabelVersionId = requiredRequestId(labelVersionId, "labelVersionId");
        String requestedRuleSetVersionId = requiredRequestId(ruleSetVersionId, "ruleSetVersionId");

        String actorId = validationIntegration.requireActor(VALIDATE_PERMISSION);
        if (actorId == null || actorId.isBlank()) {
            throw ValidationFailure.internal("The authenticated actor could not be resolved", null);
        }

        LabelValidationSnapshot label = labelSnapshots.findById(requestedLabelVersionId)
                .orElseThrow(() -> ValidationFailure.notFound(
                        "The requested label version was not found"));
        if (!label.isCurrent()) {
            throw ValidationFailure.notCurrent(
                    "Validation is permitted only for the current label version");
        }
        if (!label.ruleSetVersionId().equals(requestedRuleSetVersionId)) {
            throw ValidationFailure.precondition(
                    "The requested rule-set version does not match the label snapshot");
        }

        RuleSetVersion ruleSet = ruleSets.findActiveById(requestedRuleSetVersionId)
                .orElseThrow(() -> ValidationFailure.precondition(
                        "The requested active rule-set version is not available"));
        validateRuleSet(ruleSet, requestedRuleSetVersionId, label);

        List<RuleDefinition> activeRules = ruleSet.ruleDefinitions().stream()
                .filter(RuleDefinition::active)
                .sorted(Comparator.comparing(RuleDefinition::ruleDefinitionId))
                .toList();
        if (activeRules.isEmpty()) {
            throw ValidationFailure.precondition("The requested rule-set has no active definitions");
        }

        FormulaCompositionSnapshot formula = formulaCompositions.findById(label.formulaVersionId())
                .orElseThrow(() -> ValidationFailure.precondition(
                        "The canonical formula composition is not available"));
        validateFormula(formula, label);

        AllergenDerivation allergens = allergenFacts.derive(
                formula, requestedRuleSetVersionId, label.jurisdictionCode());
        validateDerivation(allergens, formula, requestedRuleSetVersionId, label.jurisdictionCode());

        RuleEvaluationContext context = new RuleEvaluationContext(label, ruleSet, allergens);
        Map<RuleType, RuleEvaluator> evaluatorByType = preflight(activeRules);
        List<ValidationFinding> findings = new ArrayList<>();
        for (AllergenDerivation.UnresolvedComponent unresolved : allergens.unresolvedComponents()) {
            findings.add(new ValidationFinding(
                    null,
                    "FORMULA_COMPONENT_UNRESOLVED",
                    ValidationSeverity.ERROR,
                    false,
                    true,
                    "Formula component " + unresolved.specComponentId()
                            + " has unresolved match status " + unresolved.matchStatus()));
        }

        for (RuleDefinition rule : activeRules) {
            List<ValidationFinding> ruleFindings = evaluatorByType.get(rule.ruleType()).evaluate(rule, context);
            if (ruleFindings == null || ruleFindings.isEmpty()) {
                throw ValidationFailure.internal(
                        "A validation rule produced no attributable findings", null);
            }
            for (ValidationFinding finding : ruleFindings) {
                if (finding == null || !rule.ruleDefinitionId().equals(finding.ruleDefinitionId())) {
                    throw ValidationFailure.internal(
                            "A validation rule produced an unattributed finding", null);
                }
                findings.add(finding);
            }
        }

        ValidationStatus status = findings.stream()
                .anyMatch(finding -> finding.blocking() && !finding.passed())
                ? ValidationStatus.FAILED
                : ValidationStatus.PASSED;
        return new ValidationEvaluation(actorId, label, ruleSet, allergens, findings, status);
    }

    /** Alias retained for callers that name the operation after the validation use case. */
    public ValidationEvaluation validate(String labelVersionId, String ruleSetVersionId) {
        return orchestrate(labelVersionId, ruleSetVersionId);
    }

    private void validateRuleSet(
            RuleSetVersion ruleSet, String requestedRuleSetVersionId, LabelValidationSnapshot label) {
        if (!requestedRuleSetVersionId.equals(ruleSet.ruleSetVersionId())
                || ruleSet.lifecycleStatus() != RuleSetLifecycleStatus.ACTIVE) {
            throw ValidationFailure.precondition(
                    "Only the requested ACTIVE rule-set version can be used for validation");
        }
        if (!label.jurisdictionCode().equals(ruleSet.jurisdictionCode())) {
            throw ValidationFailure.precondition(
                    "The rule-set jurisdiction does not match the label jurisdiction");
        }
        LocalDate today = LocalDate.now(clock);
        if (today.isBefore(ruleSet.effectiveFrom())
                || (ruleSet.effectiveTo() != null && today.isAfter(ruleSet.effectiveTo()))) {
            throw ValidationFailure.precondition("The requested rule-set is not effective today");
        }
    }

    private void validateFormula(FormulaCompositionSnapshot formula, LabelValidationSnapshot label) {
        if (!formula.formulaVersionId().equals(label.formulaVersionId())
                || !formula.productId().equals(label.productId())
                || !formula.isCurrentReleased()
                || formula.items().isEmpty()
                || formula.items().stream().anyMatch(item -> item.components().isEmpty())) {
            throw ValidationFailure.precondition(
                    "The canonical current released formula composition is incomplete or mismatched");
        }
    }

    private void validateDerivation(
            AllergenDerivation allergens,
            FormulaCompositionSnapshot formula,
            String ruleSetVersionId,
            String jurisdictionCode
    ) {
        if (!formula.formulaVersionId().equals(allergens.formulaVersionId())
                || !ruleSetVersionId.equals(allergens.ruleSetVersionId())
                || !jurisdictionCode.equals(allergens.jurisdictionCode())) {
            throw ValidationFailure.precondition(
                    "The allergen derivation does not match the validation input versions");
        }
    }

    private Map<RuleType, RuleEvaluator> preflight(List<RuleDefinition> activeRules) {
        var evaluatorByType = new EnumMap<RuleType, RuleEvaluator>(RuleType.class);
        for (RuleDefinition rule : activeRules) {
            try {
                evaluatorByType.putIfAbsent(rule.ruleType(), evaluators.require(rule.ruleType()));
            } catch (IllegalStateException error) {
                throw ValidationFailure.internal(
                        "Validation evaluator configuration is unavailable", error);
            }
        }
        return Map.copyOf(evaluatorByType);
    }

    private static String requiredRequestId(String value, String field) {
        if (value == null || value.isBlank()) {
            throw ValidationFailure.invalid(field + " must be supplied");
        }
        return value;
    }
}
