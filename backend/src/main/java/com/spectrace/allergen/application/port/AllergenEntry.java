package com.spectrace.allergen.application.port;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/** The four canonical fields in the Allergen HTTP resource. */
public record AllergenEntry(
        String allergenId, String allergenCode, String displayName, String jurisdictionCode
) {
    public AllergenEntry {
        allergenId = requiredText(allergenId, "allergenId");
        allergenCode = requiredText(allergenCode, "allergenCode");
        displayName = requiredText(displayName, "displayName");
        jurisdictionCode = requiredText(jurisdictionCode, "jurisdictionCode");
    }
}
