package com.spectrace.catalog.application;

/** M4 adapters must join the caller's transaction and must not trust request-body actor IDs. */
public interface CatalogIntegration {
    String requireActor(String permission);
    void audit(String actorId, String action, String entityId, String provenanceId);
}
