package com.spectrace.audit.application.port;

/** Audit for change requests and impact runs; kept apart so AuditEventPort stays a single method. */
public interface ImpactAuditPort {

    /** Append in the caller's transaction; correlationId groups a change request with its runs. */
    void recordImpactEvent(
            String actorId,
            String eventType,
            String entityType,
            String entityId,
            String correlationId,
            String provenanceId
    );
}
