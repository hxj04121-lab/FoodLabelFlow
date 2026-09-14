package com.spectrace.catalog.application.port;

import java.util.List;
import java.util.Objects;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/** A complete read of one immutable formula and its pinned specification versions. */
public record FormulaCompositionSnapshot(
        String productId,
        String formulaVersionId,
        boolean isCurrentReleased,
        List<Item> items
) {
    public FormulaCompositionSnapshot {
        productId = requiredText(productId, "productId");
        formulaVersionId = requiredText(formulaVersionId, "formulaVersionId");
        items = List.copyOf(items);
    }

    public record Item(
            String formulaItemId,
            String supplierMaterialId,
            String specificationVersionId,
            List<Component> components
    ) {
        public Item {
            formulaItemId = requiredText(formulaItemId, "formulaItemId");
            supplierMaterialId = requiredText(supplierMaterialId, "supplierMaterialId");
            specificationVersionId = requiredText(specificationVersionId, "specificationVersionId");
            components = List.copyOf(components);
        }
    }

    public record Component(
            String specComponentId,
            String ingredientId,
            String rawPhrase,
            String matchRule,
            MatchStatus matchStatus
    ) {
        public Component {
            specComponentId = requiredText(specComponentId, "specComponentId");
            ingredientId = requiredText(ingredientId, "ingredientId");
            rawPhrase = requiredText(rawPhrase, "rawPhrase");
            matchRule = requiredText(matchRule, "matchRule");
            matchStatus = Objects.requireNonNull(matchStatus, "matchStatus");
        }
    }

    /** Exact V2 tokens; unresolved rows must survive the port boundary. */
    public enum MatchStatus { MATCHED, UNMAPPED, AMBIGUOUS }
}
