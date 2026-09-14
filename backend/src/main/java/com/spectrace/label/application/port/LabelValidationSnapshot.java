package com.spectrace.label.application.port;

import java.util.List;
import java.util.Objects;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/**
 * Exact label input for validation. isCurrent is supplied by label lifecycle policy;
 * it is NOT the database is_current_published flag. A current draft can be validated.
 * Empty declarations are real input to evaluate; null means an incomplete adapter.
 */
public record LabelValidationSnapshot(
        String labelVersionId,
        String productId,
        String formulaVersionId,
        String ruleSetVersionId,
        String jurisdictionCode,
        String rawIngredientText,
        boolean isCurrent,
        String dataProvenanceId,
        List<AllergenDeclaration> declarations
) {
    public LabelValidationSnapshot {
        labelVersionId = requiredText(labelVersionId, "labelVersionId");
        productId = requiredText(productId, "productId");
        formulaVersionId = requiredText(formulaVersionId, "formulaVersionId");
        ruleSetVersionId = requiredText(ruleSetVersionId, "ruleSetVersionId");
        jurisdictionCode = requiredText(jurisdictionCode, "jurisdictionCode");
        rawIngredientText = Objects.requireNonNull(rawIngredientText, "rawIngredientText");
        dataProvenanceId = requiredText(dataProvenanceId, "dataProvenanceId");
        declarations = List.copyOf(declarations);
    }

    public record AllergenDeclaration(
            String allergenId,
            String declarationType,
            String declarationSource,
            String displayText
    ) {
        public AllergenDeclaration {
            allergenId = requiredText(allergenId, "allergenId");
            declarationType = requiredText(declarationType, "declarationType");
            declarationSource = requiredText(declarationSource, "declarationSource");
        }
    }
}
