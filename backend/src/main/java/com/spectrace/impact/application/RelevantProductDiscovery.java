package com.spectrace.impact.application;

import com.spectrace.catalog.application.port.RelevantProductLookupPort;
import com.spectrace.catalog.application.port.RelevantProductLookupPort.RelevantProduct;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/**
 * Finds the products a supplier-material change can affect (SCRUM-77). Relevance comes
 * only from M2's lookup over each product's current released FormulaVersion; there is no
 * mapping table. Runs inside the caller's analysis transaction and writes nothing.
 */
public class RelevantProductDiscovery {
    private final RelevantProductLookupPort lookup;

    public RelevantProductDiscovery(RelevantProductLookupPort lookup) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
    }

    /**
     * Products ordered by productId; empty is a completed negative result. A relevant
     * product without a current published label cannot be stored as a finding, so the
     * whole run fails with PUBLISHED_LABEL_MISSING before the first write rather than
     * dropping the product or recording a partial run.
     */
    public List<RelevantProductTarget> discover(String supplierMaterialId) {
        List<RelevantProduct> products = lookup.findProductsUsingMaterial(
                requiredText(supplierMaterialId, "supplierMaterialId"));
        var seen = new HashSet<String>();
        for (RelevantProduct product : products) {
            if (!seen.add(product.productId())) {
                throw new IllegalStateException("Relevant-product lookup returned " + product.productId() + " twice");
            }
        }
        List<String> unlabelled = products.stream()
                .filter(product -> !product.hasPublishedLabel())
                .map(RelevantProduct::productId)
                .sorted()
                .toList();
        if (!unlabelled.isEmpty()) {
            throw ImpactFailure.precondition("PUBLISHED_LABEL_MISSING",
                    "An included product has no published current label version: " + String.join(", ", unlabelled));
        }
        return products.stream()
                .map(product -> new RelevantProductTarget(
                        product.productId(),
                        product.currentFormulaVersionId(),
                        product.currentPublishedLabelVersionId(),
                        product.matchingFormulaItemIds()))
                .sorted(Comparator.comparing(RelevantProductTarget::productId))
                .toList();
    }
}
