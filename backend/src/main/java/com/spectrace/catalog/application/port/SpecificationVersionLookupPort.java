package com.spectrace.catalog.application.port;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/** Catalog-owned read of supplier materials and specification versions for change requests. */
public interface SpecificationVersionLookupPort {

    boolean supplierMaterialExists(String supplierMaterialId);

    /** Empty means the version does not exist, never a substitute version. */
    Optional<SpecificationVersionFacts> findById(String specificationVersionId);

    /**
     * The same read, locking the version row until the caller's transaction commits so
     * concurrent change requests that target one version are serialised.
     */
    Optional<SpecificationVersionFacts> lockById(String specificationVersionId);

    /** Plain batch read; versions that do not exist are simply absent from the result. */
    List<SpecificationVersionFacts> findAllById(Collection<String> specificationVersionIds);

    record SpecificationVersionFacts(
            String specificationVersionId,
            String supplierMaterialId,
            int versionNumber,
            Lifecycle lifecycle,
            LocalDate effectiveDate
    ) {
        public SpecificationVersionFacts {
            specificationVersionId = requiredText(specificationVersionId, "specificationVersionId");
            supplierMaterialId = requiredText(supplierMaterialId, "supplierMaterialId");
            if (versionNumber < 1) {
                throw new IllegalArgumentException("versionNumber must be positive");
            }
            lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
            effectiveDate = Objects.requireNonNull(effectiveDate, "effectiveDate");
        }

        public boolean isEffectiveOn(LocalDate date) {
            return !effectiveDate.isAfter(date);
        }
    }

    /** V2 chk_spec_lifecycle_v3 tokens. */
    enum Lifecycle {
        DRAFT,
        RELEASED,
        RETIRED
    }
}
