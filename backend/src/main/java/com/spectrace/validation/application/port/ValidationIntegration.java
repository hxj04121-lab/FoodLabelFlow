package com.spectrace.validation.application.port;

/** Validation-owned bridge to the existing identity and audit application APIs. */
public interface ValidationIntegration {
    /** Resolve a trusted active identity and require the supplied canonical permission. */
    String requireActor(String permission);

    /**
     * Append an attributable validation event in the caller's transaction (MANDATORY).
     * Throw on failure so the run, results and audit all roll back. No asynchronous
     * write, REQUIRES_NEW transaction, invented provenance or client-supplied actor.
     */
    void auditValidation(
            String actorId, String labelVersionId, String ruleSetVersionId,
            String validationRunId, String dataProvenanceId);
}
