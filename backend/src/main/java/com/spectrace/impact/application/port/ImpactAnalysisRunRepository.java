package com.spectrace.impact.application.port;

import com.spectrace.impact.domain.ImpactAnalysisRun;

import java.util.Optional;

public interface ImpactAnalysisRunRepository {

    /**
     * Inserts a run or returns the already persisted run for the same logical change.
     * A database uniqueness guard remains the final race-safe check.
     */
    ImpactAnalysisRun saveOrGetExisting(ImpactAnalysisRun run);

    Optional<ImpactAnalysisRun> findById(String impactAnalysisRunId);

    Optional<ImpactAnalysisRun> findByChangeRequestId(String changeRequestId);

    Optional<ImpactAnalysisRun> findByIdempotencyKey(String idempotencyKey);
}
