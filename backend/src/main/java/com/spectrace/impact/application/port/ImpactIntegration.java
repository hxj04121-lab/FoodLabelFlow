package com.spectrace.impact.application.port;

/** Impact-owned bridge to the existing identity and audit application APIs. */
public interface ImpactIntegration {

    /** Resolve a trusted active identity holding the permission; never a client-supplied actor. */
    String requireActor(Permission permission);

    /**
     * Append attributable audit events in the caller's transaction (MANDATORY).
     * Throw on failure so the change request, or the run with its findings and
     * ReviewTasks, rolls back with the audit.
     */
    void auditChangeRequestCreated(String actorId, String changeRequestId, String dataProvenanceId);

    void auditImpactRun(
            String actorId, String changeRequestId, String impactAnalysisRunId, String dataProvenanceId);

    /** Canonical V3 permission codes. */
    enum Permission {
        CREATE_CHANGE_REQUEST("CHANGE_REQUEST.CREATE"),
        RUN_IMPACT("IMPACT.RUN");

        private final String code;

        Permission(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
