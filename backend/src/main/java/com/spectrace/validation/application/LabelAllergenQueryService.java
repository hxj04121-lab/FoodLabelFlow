package com.spectrace.validation.application;

import com.spectrace.allergen.application.port.AllergenFactsPort;
import com.spectrace.catalog.application.port.FormulaCompositionPort;
import com.spectrace.label.application.port.LabelSnapshotPort;
import com.spectrace.validation.application.port.RuleSetVersionRepository;
import org.springframework.transaction.annotation.Transactional;

/** Composes owner-owned snapshots without running validation or writing audit/results. */
public class LabelAllergenQueryService {
    private final LabelSnapshotPort labels;
    private final FormulaCompositionPort formulas;
    private final RuleSetVersionRepository ruleSets;
    private final AllergenFactsPort allergens;

    public LabelAllergenQueryService(LabelSnapshotPort labels, FormulaCompositionPort formulas,
                                    RuleSetVersionRepository ruleSets, AllergenFactsPort allergens) {
        this.labels = labels;
        this.formulas = formulas;
        this.ruleSets = ruleSets;
        this.allergens = allergens;
    }

    // Business-read-only, but not a JDBC read-only transaction: existing snapshot ports use
    // locking reads. Keep their reviewed consistency guarantees in this short transaction.
    @Transactional
    public LabelAllergenFacts getByLabelVersionId(String labelVersionId) {
        if (labelVersionId == null || labelVersionId.isBlank()) {
            throw ValidationFailure.invalid("labelVersionId is required");
        }
        var label = labels.findById(labelVersionId)
                .orElseThrow(() -> ValidationFailure.notFound("The requested label version was not found"));
        if (!labelVersionId.equals(label.labelVersionId())) {
            throw ValidationFailure.internal("The label snapshot does not match the requested version", null);
        }
        var formula = formulas.findById(label.formulaVersionId())
                .orElseThrow(() -> ValidationFailure.precondition("The pinned formula composition is unavailable"));
        if (!label.formulaVersionId().equals(formula.formulaVersionId())
                || !label.productId().equals(formula.productId())
                || formula.items().isEmpty()
                || formula.items().stream().anyMatch(item -> item.components().isEmpty())) {
            throw ValidationFailure.precondition("The pinned formula composition is incomplete or mismatched");
        }
        // Display may read historical/retired bindings; only POST validation requires current/ACTIVE inputs.
        var ruleSet = ruleSets.findById(label.ruleSetVersionId())
                .orElseThrow(() -> ValidationFailure.precondition("The pinned rule-set version is unavailable"));
        if (!label.ruleSetVersionId().equals(ruleSet.ruleSetVersionId())
                || !label.jurisdictionCode().equals(ruleSet.jurisdictionCode())) {
            throw ValidationFailure.precondition("The pinned rule-set version does not match the label jurisdiction");
        }
        var derived = allergens.derive(formula, label.ruleSetVersionId(), label.jurisdictionCode());
        if (!label.formulaVersionId().equals(derived.formulaVersionId())
                || !label.ruleSetVersionId().equals(derived.ruleSetVersionId())
                || !label.jurisdictionCode().equals(derived.jurisdictionCode())) {
            throw ValidationFailure.internal("The derived facts do not match the requested label binding", null);
        }
        return new LabelAllergenFacts(labelVersionId, derived.formulaVersionId(), derived.ruleSetVersionId(),
                derived.jurisdictionCode(), derived.facts(), derived.unresolvedComponents());
    }
}
