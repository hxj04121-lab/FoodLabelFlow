package com.spectrace.impact.application.strategy;

import com.spectrace.impact.domain.ImpactClassification;
import com.spectrace.impact.domain.ImpactFinding;

import java.util.List;
import java.util.Objects;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/**
 * A strategy's verdict for one product, before the run assigns identifiers. The
 * classification follows from missingAllergenCodes alone, so the two cannot disagree.
 */
public record ProductImpactAssessment(
        String productId,
        String currentFormulaVersionId,
        String proposedFormulaVersionId,
        String currentLabelVersionId,
        List<String> missingAllergenCodes,
        String explanation
) {
    public ProductImpactAssessment {
        productId = requiredText(productId, "productId");
        currentFormulaVersionId = requiredText(currentFormulaVersionId, "currentFormulaVersionId");
        proposedFormulaVersionId = requiredText(proposedFormulaVersionId, "proposedFormulaVersionId");
        currentLabelVersionId = requiredText(currentLabelVersionId, "currentLabelVersionId");
        missingAllergenCodes = List.copyOf(Objects.requireNonNull(missingAllergenCodes, "missingAllergenCodes"));
        explanation = requiredText(explanation, "explanation");
    }

    public ImpactClassification classification() {
        return missingAllergenCodes.isEmpty() ? ImpactClassification.NO_ACTION : ImpactClassification.REVIEW_REQUIRED;
    }

    /** The persisted form; ImpactFinding re-checks every column invariant. */
    public ImpactFinding toFinding(String impactFindingId, String impactAnalysisRunId, String dataProvenanceId) {
        return new ImpactFinding(impactFindingId, impactAnalysisRunId, productId, currentFormulaVersionId,
                proposedFormulaVersionId, currentLabelVersionId, classification(), missingAllergenCodes,
                explanation, dataProvenanceId);
    }
}
