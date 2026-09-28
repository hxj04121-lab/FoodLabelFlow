package com.spectrace.impact.application.port;

import com.spectrace.impact.domain.ImpactFinding;

import java.util.List;

/** Implemented by M5 (SCRUM-51); at most one finding per run and product. */
public interface ImpactFindingRepository {

    void saveAll(String impactAnalysisRunId, List<ImpactFinding> findings);

    /** Findings of one run ordered by productId. Empty means the run found no relevant product. */
    List<ImpactFinding> findByRunId(String impactAnalysisRunId);
}
