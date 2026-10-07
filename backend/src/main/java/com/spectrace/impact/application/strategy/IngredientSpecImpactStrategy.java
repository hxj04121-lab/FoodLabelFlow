package com.spectrace.impact.application.strategy;

import com.spectrace.allergen.application.port.AllergenDerivation;
import com.spectrace.allergen.application.port.AllergenFact;
import com.spectrace.allergen.application.port.AllergenFactsPort;
import com.spectrace.catalog.application.port.FormulaCompositionPort;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot;
import com.spectrace.catalog.application.port.FormulaCompositionSnapshot.Item;
import com.spectrace.impact.application.ImpactFailure;
import com.spectrace.impact.application.RelevantProductTarget;
import com.spectrace.impact.domain.ChangeRequest;
import com.spectrace.impact.domain.ChangeType;
import com.spectrace.label.application.port.LabelSnapshotPort;
import com.spectrace.label.application.port.LabelValidationSnapshot;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/**
 * INGREDIENT_SPEC impact: derive allergens from the adopted FormulaVersion N+1 with the
 * Sprint 2 derivation and compare them with the CONTAINS declarations of the product's
 * published label, which still sits on formula N. Nothing missing is NO_ACTION; anything
 * missing is REVIEW_REQUIRED with the missing codes.
 *
 * <p>An empty derivation is a NO_ACTION basis only when it is complete: every item of N+1
 * has components and every component is MATCHED. An incomplete derivation can still prove
 * a missing allergen, but it can never prove that nothing is missing.</p>
 */
public final class IngredientSpecImpactStrategy implements ImpactStrategy {
    private static final String CONTAINS = "CONTAINS";

    private final FormulaCompositionPort formulas;
    private final LabelSnapshotPort labels;
    private final AllergenFactsPort allergens;

    public IngredientSpecImpactStrategy(
            FormulaCompositionPort formulas, LabelSnapshotPort labels, AllergenFactsPort allergens) {
        this.formulas = Objects.requireNonNull(formulas, "formulas");
        this.labels = Objects.requireNonNull(labels, "labels");
        this.allergens = Objects.requireNonNull(allergens, "allergens");
    }

    @Override
    public ChangeType changeType() {
        return ChangeType.INGREDIENT_SPEC;
    }

    @Override
    public ProductImpactAssessment assess(
            ChangeRequest change, RelevantProductTarget product, String ruleSetVersionId) {
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(product, "product");
        requiredText(ruleSetVersionId, "ruleSetVersionId");
        if (change.changeType() != changeType()) {
            throw new IllegalArgumentException("Change " + change.changeRequestId() + " is not an INGREDIENT_SPEC change");
        }
        String targetSpecificationId = change.versionChange().toVersionId();
        String productId = product.productId();

        // The published label is read by its exact ID: after adoption its formula N is no
        // longer current, so the label snapshot's isCurrent flag is expectedly false.
        LabelValidationSnapshot label = labels.findById(product.currentLabelVersionId())
                .filter(found -> found.productId().equals(productId))
                .orElseThrow(() -> new IllegalStateException(
                        "Published label " + product.currentLabelVersionId() + " of " + productId + " is unavailable"));
        FormulaCompositionSnapshot proposed = formulas.findById(product.currentFormulaVersionId())
                .filter(found -> found.productId().equals(productId) && found.isCurrentReleased())
                .orElseThrow(() -> new IllegalStateException(
                        "Current formula " + product.currentFormulaVersionId() + " of " + productId + " is unavailable"));

        requireAdopted(product, proposed, targetSpecificationId);
        if (proposed.formulaVersionId().equals(label.formulaVersionId())) {
            throw ImpactFailure.conflict("The published label of " + productId
                    + " is already bound to a formula using specification " + targetSpecificationId);
        }

        AllergenDerivation derivation = allergens.derive(proposed, ruleSetVersionId, label.jurisdictionCode());
        if (!derivation.formulaVersionId().equals(proposed.formulaVersionId())
                || !derivation.ruleSetVersionId().equals(ruleSetVersionId)
                || !derivation.jurisdictionCode().equals(label.jurisdictionCode())) {
            throw new IllegalStateException("The allergen derivation does not match formula "
                    + proposed.formulaVersionId());
        }

        Set<String> declared = label.declarations().stream()
                .filter(declaration -> CONTAINS.equals(declaration.declarationType()))
                .map(LabelValidationSnapshot.AllergenDeclaration::allergenId)
                .collect(Collectors.toSet());
        List<String> derivedCodes = derivation.facts().stream().map(AllergenFact::allergenCode).sorted().toList();
        List<String> missing = derivation.facts().stream()
                .filter(fact -> !declared.contains(fact.allergenId()))
                .map(AllergenFact::allergenCode)
                .sorted()
                .toList();
        int incomplete = incompleteInputs(proposed, derivation);

        if (missing.isEmpty() && incomplete > 0) {
            // Cannot be NO_ACTION, and REVIEW_REQUIRED needs a missing code: fail the whole run.
            throw new IllegalStateException("Formula " + proposed.formulaVersionId() + " has " + incomplete
                    + " unresolved or empty specification input(s); its allergens cannot be confirmed");
        }

        String basis = "Formula " + proposed.formulaVersionId() + " with specification " + targetSpecificationId;
        String labelRef = "published label " + label.labelVersionId();
        String explanation;
        if (!missing.isEmpty()) {
            explanation = basis + " derives " + String.join(", ", missing) + ", not declared as CONTAINS on "
                    + labelRef + "." + (incomplete > 0
                    ? " " + incomplete + " unresolved or empty specification input(s) also need review."
                    : "");
        } else if (derivedCodes.isEmpty()) {
            explanation = basis + " derives no allergens from fully matched components; "
                    + labelRef + " needs no new declaration.";
        } else {
            explanation = basis + " derives " + String.join(", ", derivedCodes)
                    + ", all already declared as CONTAINS on " + labelRef + ".";
        }
        return assessment(product, label, proposed, missing, explanation);
    }

    /** Every item that made the product relevant must now pin the target specification. */
    private static void requireAdopted(
            RelevantProductTarget product, FormulaCompositionSnapshot proposed, String targetSpecificationId) {
        List<Item> matching = proposed.items().stream()
                .filter(item -> product.matchingFormulaItemIds().contains(item.formulaItemId()))
                .toList();
        if (matching.size() != product.matchingFormulaItemIds().size()) {
            throw new IllegalStateException("Formula " + proposed.formulaVersionId()
                    + " no longer contains every matching item of " + product.productId());
        }
        if (matching.stream().anyMatch(item -> !item.specificationVersionId().equals(targetSpecificationId))) {
            throw ImpactFailure.precondition("FORMULA_ADOPTION_PENDING", "The current formula of "
                    + product.productId() + " has not adopted specification " + targetSpecificationId);
        }
    }

    private static int incompleteInputs(FormulaCompositionSnapshot formula, AllergenDerivation derivation) {
        int emptyItems = (int) formula.items().stream().filter(item -> item.components().isEmpty()).count();
        return (formula.items().isEmpty() ? 1 : 0) + emptyItems + derivation.unresolvedComponents().size();
    }

    private static ProductImpactAssessment assessment(
            RelevantProductTarget product,
            LabelValidationSnapshot label,
            FormulaCompositionSnapshot proposed,
            List<String> missing,
            String explanation
    ) {
        return new ProductImpactAssessment(product.productId(), label.formulaVersionId(),
                proposed.formulaVersionId(), label.labelVersionId(), missing, explanation);
    }
}
