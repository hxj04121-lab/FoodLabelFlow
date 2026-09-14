package com.spectrace.allergen.application.port;

import com.spectrace.catalog.application.port.FormulaCompositionSnapshot.MatchStatus;

import java.util.List;
import java.util.Objects;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/** Version-scoped derivation; unresolved inputs are never silently discarded. */
public record AllergenDerivation(
        String formulaVersionId,
        String ruleSetVersionId,
        String jurisdictionCode,
        List<AllergenFact> facts,
        List<UnresolvedComponent> unresolvedComponents
) {
    public AllergenDerivation {
        formulaVersionId = requiredText(formulaVersionId, "formulaVersionId");
        ruleSetVersionId = requiredText(ruleSetVersionId, "ruleSetVersionId");
        jurisdictionCode = requiredText(jurisdictionCode, "jurisdictionCode");
        facts = List.copyOf(facts);
        unresolvedComponents = List.copyOf(unresolvedComponents);
    }

    public record UnresolvedComponent(
            String formulaItemId,
            String specificationVersionId,
            String specComponentId,
            String ingredientId,
            String rawPhrase,
            String matchRule,
            MatchStatus matchStatus
    ) {
        public UnresolvedComponent {
            formulaItemId = requiredText(formulaItemId, "formulaItemId");
            specificationVersionId = requiredText(specificationVersionId, "specificationVersionId");
            specComponentId = requiredText(specComponentId, "specComponentId");
            ingredientId = requiredText(ingredientId, "ingredientId");
            rawPhrase = requiredText(rawPhrase, "rawPhrase");
            matchRule = requiredText(matchRule, "matchRule");
            matchStatus = Objects.requireNonNull(matchStatus, "matchStatus");
            if (matchStatus == MatchStatus.MATCHED) {
                throw new IllegalArgumentException("An unresolved component cannot be MATCHED");
            }
        }
    }
}
