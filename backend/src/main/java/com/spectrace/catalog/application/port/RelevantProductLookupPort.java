package com.spectrace.catalog.application.port;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/**
 * Supplier-material to product lookup supplied by M2 (SCRUM-48). Relevance is read
 * only from the current released FormulaVersion's FormulaItem.supplier_material_id;
 * there is no synthetic mapping table.
 */
public interface RelevantProductLookupPort {

    /**
     * Every product whose current released formula has at least one item using the
     * material, ordered by productId. Products that use it only in historical formula
     * versions are excluded. Empty is a completed negative lookup, not missing data.
     * Joins the caller's transaction.
     */
    List<RelevantProduct> findProductsUsingMaterial(String supplierMaterialId);

    /**
     * currentPublishedLabelVersionId is null when the product has no published label;
     * the impact core must report that case explicitly rather than drop the product.
     */
    record RelevantProduct(
            String productId,
            String currentFormulaVersionId,
            String currentPublishedLabelVersionId,
            List<String> matchingFormulaItemIds
    ) {
        public RelevantProduct {
            productId = requiredText(productId, "productId");
            currentFormulaVersionId = requiredText(currentFormulaVersionId, "currentFormulaVersionId");
            if (currentPublishedLabelVersionId != null) {
                currentPublishedLabelVersionId = requiredText(
                        currentPublishedLabelVersionId, "currentPublishedLabelVersionId");
            }
            matchingFormulaItemIds = List.copyOf(
                    Objects.requireNonNull(matchingFormulaItemIds, "matchingFormulaItemIds"));
            if (matchingFormulaItemIds.isEmpty()) {
                throw new IllegalArgumentException("A relevant product requires a matching formula item");
            }
            var seen = new HashSet<String>();
            for (String formulaItemId : matchingFormulaItemIds) {
                if (!seen.add(requiredText(formulaItemId, "matchingFormulaItemId"))) {
                    throw new IllegalArgumentException("Duplicate formula item: " + formulaItemId);
                }
            }
        }

        public boolean hasPublishedLabel() {
            return currentPublishedLabelVersionId != null;
        }
    }
}
