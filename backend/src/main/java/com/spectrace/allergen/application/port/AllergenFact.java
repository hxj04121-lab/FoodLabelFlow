package com.spectrace.allergen.application.port;

import java.util.List;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/** One derived allergen, retaining every contributing relational path (BR-03). */
public record AllergenFact(
        String allergenId, String allergenCode, List<DerivationEvidence> derivationEvidence
) {
    public AllergenFact {
        allergenId = requiredText(allergenId, "allergenId");
        allergenCode = requiredText(allergenCode, "allergenCode");
        derivationEvidence = List.copyOf(derivationEvidence);
        if (derivationEvidence.isEmpty()) {
            throw new IllegalArgumentException("A derived allergen requires relational evidence");
        }
    }

    public record DerivationEvidence(
            String formulaItemId,
            String specificationVersionId,
            String specComponentId,
            String ingredientId,
            String ingredientAllergenId,
            String evidenceRule,
            String dataProvenanceId
    ) {
        public DerivationEvidence {
            formulaItemId = requiredText(formulaItemId, "formulaItemId");
            specificationVersionId = requiredText(specificationVersionId, "specificationVersionId");
            specComponentId = requiredText(specComponentId, "specComponentId");
            ingredientId = requiredText(ingredientId, "ingredientId");
            ingredientAllergenId = requiredText(ingredientAllergenId, "ingredientAllergenId");
            evidenceRule = requiredText(evidenceRule, "evidenceRule");
            dataProvenanceId = requiredText(dataProvenanceId, "dataProvenanceId");
        }
    }
}
