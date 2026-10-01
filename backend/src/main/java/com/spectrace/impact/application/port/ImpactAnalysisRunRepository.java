package com.spectrace.impact.application.port;

import com.spectrace.impact.domain.ImpactAnalysisRun;

import java.util.List;
import java.util.Optional;

/** Implemented by M5 (SCRUM-51); writes join the caller's transaction. */
public interface ImpactAnalysisRunRepository {

    /** A duplicate change request raises ImpactRunAlreadyExistsException with the committed winner. */
    void save(ImpactAnalysisRun run);

    Optional<ImpactAnalysisRun> findById(String impactAnalysisRunId);

    /** List shape retained for M1 callers; live persistence permits one analysis per change request. */
    List<ImpactAnalysisRun> findByChangeRequestId(String changeRequestId);
}
