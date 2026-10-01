package com.spectrace.impact.application.port;

import com.spectrace.impact.domain.ImpactAnalysisRun;

import java.util.List;
import java.util.Optional;

/** Implemented by M5 (SCRUM-51); writes join the caller's transaction. */
public interface ImpactAnalysisRunRepository {

    void save(ImpactAnalysisRun run);

    Optional<ImpactAnalysisRun> findById(String impactAnalysisRunId);

    /** Every run for the change request, oldest first; the basis for idempotent re-triggering. */
    List<ImpactAnalysisRun> findByChangeRequestId(String changeRequestId);
}
