package com.spectrace.impact.application.port;

import java.util.Optional;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/** Catalog-backed read of one ingredient specification version; never a foreign SQL join. */
public interface SpecificationVersionLookupPort {

    /** Empty means the version does not exist (404), never a substitute version. */
    Optional<SpecificationVersionFacts> findById(String specificationVersionId);

    record SpecificationVersionFacts(
            String specificationVersionId,
            String supplierMaterialId,
            int versionNumber,
            boolean released
    ) {
        public SpecificationVersionFacts {
            specificationVersionId = requiredText(specificationVersionId, "specificationVersionId");
            supplierMaterialId = requiredText(supplierMaterialId, "supplierMaterialId");
            if (versionNumber < 1) {
                throw new IllegalArgumentException("versionNumber must be positive");
            }
        }
    }
}
