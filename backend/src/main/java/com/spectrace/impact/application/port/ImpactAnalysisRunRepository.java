package com.spectrace.impact.application.port;

import com.spectrace.impact.domain.ImpactAnalysisRun;

import java.util.List;
import java.util.Optional;

public interface ImpactAnalysisRunRepository {

    void save(ImpactAnalysisRun run);

    Optional<ImpactAnalysisRun> findById(String impactAnalysisRunId);

    List<ImpactAnalysisRun> findByChangeRequestId(String changeRequestId);
}
