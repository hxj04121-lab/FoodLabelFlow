package com.spectrace.impact.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import static com.spectrace.shared.contract.ContractValues.requiredText;

public record ImpactFinding(
        String impactFindingId,
        String impactAnalysisRunId,
        String productId,
        String currentFormulaVersionId,
        String proposedFormulaVersionId,
        String currentLabelVersionId,
        ImpactClassification classification,
        List<String> missingAllergenCodes,
        String explanation,
        String dataProvenanceId
) {
    public ImpactFinding {
        impactFindingId = requiredText(impactFindingId, "impactFindingId");
        impactAnalysisRunId = requiredText(impactAnalysisRunId, "impactAnalysisRunId");
        productId = requiredText(productId, "productId");
        currentFormulaVersionId = requiredText(currentFormulaVersionId, "currentFormulaVersionId");
        if (proposedFormulaVersionId != null) {
            proposedFormulaVersionId = requiredText(proposedFormulaVersionId, "proposedFormulaVersionId");
            if (proposedFormulaVersionId.equals(currentFormulaVersionId)) {
                throw new IllegalArgumentException("proposedFormulaVersionId must differ from the current formula");
            }
        }
        currentLabelVersionId = requiredText(currentLabelVersionId, "currentLabelVersionId");
        classification = Objects.requireNonNull(classification, "classification");
        missingAllergenCodes = sortedDistinctCodes(missingAllergenCodes);
        if (classification.requiresReviewTask() == missingAllergenCodes.isEmpty()) {
            throw new IllegalArgumentException(
                    "REVIEW_REQUIRED needs missing allergen codes and NO_ACTION must have none");
        }
        explanation = requiredText(explanation, "explanation");
        dataProvenanceId = requiredText(dataProvenanceId, "dataProvenanceId");
    }

    public boolean requiresReviewTask() {
        return classification.requiresReviewTask();
    }

    private static List<String> sortedDistinctCodes(List<String> codes) {
        Objects.requireNonNull(codes, "missingAllergenCodes");
        var seen = new HashSet<String>();
        for (String code : codes) {
            if (!seen.add(requiredText(code, "missingAllergenCode"))) {
                throw new IllegalArgumentException("Duplicate missing allergen code: " + code);
            }
        }
        return codes.stream().sorted().toList();
    }
}
