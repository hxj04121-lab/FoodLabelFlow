package com.spectrace.catalog.application;

import com.spectrace.catalog.domain.CatalogCommands.AdoptSpecification;
import com.spectrace.catalog.domain.CatalogFailure;
import com.spectrace.catalog.infrastructure.CatalogStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.spectrace.catalog.infrastructure.CatalogStore.Kind.SPECIFICATION;

/** Runs one independently committed, replay-safe adoption per current product. */
@Service
public class BatchSpecificationAdoptionService {
    private final CatalogStore store;
    private final CatalogService catalog;
    private final TransactionTemplate productTransaction;

    public BatchSpecificationAdoptionService(CatalogStore store, CatalogService catalog,
                                             org.springframework.transaction.PlatformTransactionManager manager) {
        this.store = store;
        this.catalog = catalog;
        this.productTransaction = new TransactionTemplate(manager);
        this.productTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public BatchSummary adopt(String targetSpecificationVersionId) {
        if (targetSpecificationVersionId == null || targetSpecificationVersionId.isBlank()
                || targetSpecificationVersionId.length() > 100) {
            throw CatalogFailure.invalid("targetSpecificationVersionId is required");
        }
        final String targetId = targetSpecificationVersionId.trim();
        catalog.requireBatchAdoptionPermission();
        Map<String, Object> target = store.get(SPECIFICATION, targetId, false);
        catalog.requireBatchAdoptionTarget(target);
        String materialId = (String) target.get("supplier_material_id");
        List<Map<String, Object>> products = store.currentProductsUsingMaterial(materialId);
        List<Result> results = new ArrayList<>();
        for (Map<String, Object> product : products) {
            String productId = (String) product.get("product_id");
            try {
                results.add(productTransaction.execute(status -> adoptOne(productId, targetId)));
            } catch (CatalogFailure failure) {
                // A concurrent identical request may have committed while this one waited
                // on the product lock. Re-read its durable result after our transaction rolls back.
                Map<String, Object> prior = store.findBatchAdoption(productId, targetId);
                if (prior != null) {
                    results.add(new Result(productId, "ADOPTED", (String) prior.get("formula_version_id"), true, null, null));
                } else if (failure.status() == 409 || failure.status() == 422 || failure.status() == 404) {
                    results.add(new Result(productId, "SKIPPED", null, false, failure.code(), failure.getMessage()));
                } else {
                    results.add(new Result(productId, "FAILED", null, false, failure.code(), "Adoption failed; retry after resolving the catalog error."));
                }
            } catch (RuntimeException failure) {
                results.add(new Result(productId, "FAILED", null, false, "ADOPTION_FAILED", "Adoption failed; retry after checking the service."));
            }
        }
        int adopted = (int) results.stream().filter(result -> result.status().equals("ADOPTED")).count();
        int skipped = (int) results.stream().filter(result -> result.status().equals("SKIPPED")).count();
        int failed = results.size() - adopted - skipped;
        return new BatchSummary(targetId, results.size(), adopted, skipped, failed, results);
    }

    private Result adoptOne(String productId, String targetSpecificationVersionId) {
        Map<String, Object> prior = store.findBatchAdoption(productId, targetSpecificationVersionId);
        if (prior != null) {
            return new Result(productId, "ADOPTED", (String) prior.get("formula_version_id"), true, null, null);
        }
        Map<String, Object> product = store.get(CatalogStore.Kind.PRODUCT, productId, true);
        String sourceId = (String) product.get("current_formula_version_id");
        if (sourceId == null) {
            return new Result(productId, "SKIPPED", null, false, "NO_CURRENT_FORMULA", "Product has no current released formula.");
        }
        Map<String, Object> formula = catalog.adoptSpecification(productId,
                new AdoptSpecification(sourceId, targetSpecificationVersionId));
        String resultId = (String) formula.get("formula_version_id");
        store.recordBatchAdoption(productId, sourceId, targetSpecificationVersionId, resultId);
        return new Result(productId, "ADOPTED", resultId, false, null, null);
    }

    public record BatchSummary(String targetSpecificationVersionId, int candidateCount,
                               int adoptedCount, int skippedCount, int failedCount, List<Result> results) {
        public BatchSummary { results = List.copyOf(results); }
    }

    public record Result(String productId, String status, String formulaVersionId,
                         boolean replayed, String errorCode, String message) { }
}
