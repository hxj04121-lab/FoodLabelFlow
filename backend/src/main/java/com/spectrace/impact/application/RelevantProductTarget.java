package com.spectrace.impact.application;

import java.util.List;
import java.util.Objects;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/**
 * A relevant product that impact analysis can classify: unlike the catalog lookup row,
 * currentLabelVersionId is always present, matching impact_finding.current_label_version_id.
 */
public record RelevantProductTarget(
        String productId,
        String currentFormulaVersionId,
        String currentLabelVersionId,
        List<String> matchingFormulaItemIds
) {
    public RelevantProductTarget {
        productId = requiredText(productId, "productId");
        currentFormulaVersionId = requiredText(currentFormulaVersionId, "currentFormulaVersionId");
        currentLabelVersionId = requiredText(currentLabelVersionId, "currentLabelVersionId");
        matchingFormulaItemIds = List.copyOf(Objects.requireNonNull(matchingFormulaItemIds, "matchingFormulaItemIds"));
        if (matchingFormulaItemIds.isEmpty()) {
            throw new IllegalArgumentException("A relevant product requires a matching formula item");
        }
    }
}
