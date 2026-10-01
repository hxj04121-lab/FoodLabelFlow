package com.spectrace.impact.application;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/** A parsed POST /api/v1/change-requests body; changeType is always INGREDIENT_SPEC in Sprint 3. */
public record CreateIngredientSpecChange(
        String supplierMaterialId,
        String previousSpecificationVersionId,
        String targetSpecificationVersionId,
        String description
) {
    /** change_request.description is VARCHAR(1000); the contract caps it at the same length. */
    public static final int MAX_DESCRIPTION_LENGTH = 1000;

    public CreateIngredientSpecChange {
        supplierMaterialId = requiredText(supplierMaterialId, "supplierMaterialId");
        previousSpecificationVersionId = requiredText(
                previousSpecificationVersionId, "previousSpecificationVersionId");
        targetSpecificationVersionId = requiredText(targetSpecificationVersionId, "targetSpecificationVersionId");
        description = requiredText(description, "description");
        if (description.codePointCount(0, description.length()) > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("description must be at most " + MAX_DESCRIPTION_LENGTH + " characters");
        }
    }
}
