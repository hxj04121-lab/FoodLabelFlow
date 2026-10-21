package com.spectrace.catalog.application;

import com.spectrace.catalog.domain.CatalogCommands.AdoptSpecification;
import com.spectrace.catalog.domain.CatalogFailure;
import com.spectrace.catalog.infrastructure.CatalogStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.spectrace.catalog.infrastructure.CatalogStore.Kind.PRODUCT;
import static com.spectrace.catalog.infrastructure.CatalogStore.Kind.SPECIFICATION;

/**
 * SCRUM-85: adopts one released target specification into FormulaVersion N+1 for every
 * product whose current released formula uses its material. Each product runs M2's
 * single-product adoption in its own transaction together with a replay marker, so one
 * failing product never rolls back the others and a repeated request returns the saved result.
 */
@Service
public class BatchSpecificationAdoptionService {
    public static final String ADOPTED = "ADOPTED";
    public static final String SKIPPED = "SKIPPED";
    public static final String FAILED = "FAILED";

    private static final Logger LOG = LoggerFactory.getLogger(BatchSpecificationAdoptionService.class);

    private final CatalogStore store;
    private final CatalogService catalog;
    private final TransactionTemplate productTransaction;

    public BatchSpecificationAdoptionService(CatalogStore store, CatalogService catalog,
                                             PlatformTransactionManager transactions) {
        this.store = store;
        this.catalog = catalog;
        this.productTransaction = new TransactionTemplate(transactions);
        this.productTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public BatchSummary adopt(String targetSpecificationVersionId) {
        if (targetSpecificationVersionId == null || targetSpecificationVersionId.isBlank()
                || targetSpecificationVersionId.length() > 100) {
            throw CatalogFailure.invalid("targetSpecificationVersionId is required");
        }
        String targetId = targetSpecificationVersionId.trim();
        // Deny before any candidate read so an unauthorized caller learns nothing about products.
        catalog.requireBatchAdoptionPermission();
        Map<String, Object> target = store.get(SPECIFICATION, targetId, false);
        catalog.requireBatchAdoptionTarget(target);
        List<Map<String, Object>> products =
                store.currentProductsUsingMaterial((String) target.get("supplier_material_id"));
        List<Result> results = new ArrayList<>();
        for (Map<String, Object> product : products) {
            results.add(adoptIndependently((String) product.get("product_id"), targetId));
        }
        return new BatchSummary(targetId, results);
    }

    private Result adoptIndependently(String productId, String targetId) {
        try {
            return productTransaction.execute(status -> adoptOne(productId, targetId));
        } catch (RuntimeException failure) {
            // A concurrent identical request may have committed while this one waited on the
            // product lock; once our transaction has rolled back, its durable result wins.
            Map<String, Object> prior = store.findBatchAdoption(productId, targetId);
            if (prior != null) return Result.adopted(productId, (String) prior.get("formula_version_id"), true);
            if (failure instanceof CatalogFailure catalogFailure && isSkippable(catalogFailure)) {
                return new Result(productId, SKIPPED, null, false, catalogFailure.code(), catalogFailure.getMessage());
            }
            LOG.warn("Batch adoption of {} for product {} failed and was rolled back", targetId, productId, failure);
            String code = failure instanceof CatalogFailure catalogFailure ? catalogFailure.code() : "ADOPTION_FAILED";
            return new Result(productId, FAILED, null, false, code,
                    "Adoption failed and was rolled back; retry after resolving the error.");
        }
    }

    /** The product no longer qualifies (changed, already on the target, or incompatible): not an error. */
    private static boolean isSkippable(CatalogFailure failure) {
        return failure.status() == 404 || failure.status() == 409 || failure.status() == 422;
    }

    private Result adoptOne(String productId, String targetId) {
        // Lock the product before any nonlocking read. Under REPEATABLE READ the first plain SELECT
        // fixes the snapshot; taken after the lock, it includes every formula committed by whoever
        // held the lock before us, so the current formula's items can never be hidden from it.
        Map<String, Object> product = store.get(PRODUCT, productId, true);
        Map<String, Object> prior = store.findBatchAdoption(productId, targetId);
        if (prior != null) return Result.adopted(productId, (String) prior.get("formula_version_id"), true);
        // The formula current now, under the lock, is the one adopted, not the one discovered.
        String sourceId = (String) product.get("current_formula_version_id");
        if (sourceId == null) {
            return new Result(productId, SKIPPED, null, false, "NO_CURRENT_FORMULA",
                    "Product has no current released formula.");
        }
        Map<String, Object> formula = catalog.adoptSpecification(productId, new AdoptSpecification(sourceId, targetId));
        String formulaVersionId = (String) formula.get("formula_version_id");
        store.recordBatchAdoption(productId, sourceId, targetId, formulaVersionId);
        return Result.adopted(productId, formulaVersionId, false);
    }

    public record BatchSummary(String targetSpecificationVersionId, int candidateCount,
                               int adoptedCount, int skippedCount, int failedCount, List<Result> results) {
        public BatchSummary {
            results = List.copyOf(results);
        }

        BatchSummary(String targetSpecificationVersionId, List<Result> results) {
            this(targetSpecificationVersionId, results.size(), count(results, ADOPTED),
                    count(results, SKIPPED), count(results, FAILED), results);
        }

        private static int count(List<Result> results, String status) {
            return (int) results.stream().filter(result -> result.status().equals(status)).count();
        }
    }

    public record Result(String productId, String status, String formulaVersionId,
                         boolean replayed, String errorCode, String message) {
        static Result adopted(String productId, String formulaVersionId, boolean replayed) {
            return new Result(productId, ADOPTED, formulaVersionId, replayed, null, null);
        }
    }
}
