package com.spectrace.impact.domain;

import java.util.List;
import java.util.Objects;

public record ImpactFinding(
        String impactFindingId,
        String impactAnalysisRunId,
        String productId,
        String currentFormulaVersionId,
        String proposedFormulaVersionId,
        String currentLabelVersionId,
        ImpactFindingClassification classification,
        List<String> missingAllergenCodes,
        String explanation,
        String dataProvenanceId
) {
    public ImpactFinding {
        impactFindingId = required(impactFindingId, "impactFindingId");
        impactAnalysisRunId = required(impactAnalysisRunId, "impactAnalysisRunId");
        productId = required(productId, "productId");
        currentFormulaVersionId = required(currentFormulaVersionId, "currentFormulaVersionId");
        currentLabelVersionId = required(currentLabelVersionId, "currentLabelVersionId");
        classification = Objects.requireNonNull(classification, "classification");
        missingAllergenCodes = List.copyOf(Objects.requireNonNull(missingAllergenCodes, "missingAllergenCodes"));
        explanation = required(explanation, "explanation");
        dataProvenanceId = required(dataProvenanceId, "dataProvenanceId");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
