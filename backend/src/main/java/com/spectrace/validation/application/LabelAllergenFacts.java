package com.spectrace.validation.application;

import com.spectrace.allergen.application.port.AllergenDerivation;
import com.spectrace.allergen.application.port.AllergenFact;

import java.util.List;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/** Display facts for one exact label binding; deliberately not a validation result. */
public record LabelAllergenFacts(
        String labelVersionId,
        String formulaVersionId,
        String ruleSetVersionId,
        String jurisdictionCode,
        List<AllergenFact> facts,
        List<AllergenDerivation.UnresolvedComponent> unresolvedComponents
) {
    public LabelAllergenFacts {
        labelVersionId = requiredText(labelVersionId, "labelVersionId");
        formulaVersionId = requiredText(formulaVersionId, "formulaVersionId");
        ruleSetVersionId = requiredText(ruleSetVersionId, "ruleSetVersionId");
        jurisdictionCode = requiredText(jurisdictionCode, "jurisdictionCode");
        facts = List.copyOf(facts);
        unresolvedComponents = List.copyOf(unresolvedComponents);
    }
}
