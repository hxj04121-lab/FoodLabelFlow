package com.spectrace.impact.application;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/** A parsed POST /api/v1/change-requests body; changeType is always INGREDIENT_SPEC in Sprint 3. */
public record CreateIngredientSpecChange(
        String supplierMaterialId,
        String previousSpecificationVersionId,
        String targetSpecificationVersionId
) {
    public CreateIngredientSpecChange {
        supplierMaterialId = requiredText(supplierMaterialId, "supplierMaterialId");
        previousSpecificationVersionId = requiredText(
                previousSpecificationVersionId, "previousSpecificationVersionId");
        targetSpecificationVersionId = requiredText(targetSpecificationVersionId, "targetSpecificationVersionId");
    }
}
