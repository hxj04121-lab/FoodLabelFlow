package com.spectrace.catalog.application;

/** M4 adapters must join the caller's transaction and must not trust request-body actor IDs. */
public interface CatalogIntegration {
    String requireActor(String permission);
    void audit(String actorId, String action, String entityId, String provenanceId);
    /** Existing adapters retain their catalog event; request adapters also record the adoption references. */
    default void auditSpecificationAdoption(String actorId, String newFormulaVersionId,
                                           String sourceFormulaVersionId, String targetSpecificationVersionId,
                                           String provenanceId) {
        audit(actorId, "FORMULA_SPECIFICATION_ADOPTED", newFormulaVersionId, provenanceId);
    }
}
