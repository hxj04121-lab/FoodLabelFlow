package com.spectrace.audit.application.port;

/** Impact-owned audit contract; implementations must join the caller's transaction. */
public interface ImpactAuditEventPort {

    void recordImpactEvent(
            String actorId,
            String impactAnalysisRunId,
            String changeRequestId,
            String outcome,
            String provenanceId
    );
}
