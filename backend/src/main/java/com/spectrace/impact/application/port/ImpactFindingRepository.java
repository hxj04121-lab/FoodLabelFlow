package com.spectrace.impact.application.port;

import com.spectrace.impact.domain.ImpactFinding;

import java.util.List;

public interface ImpactFindingRepository {

    void saveAll(String impactAnalysisRunId, List<ImpactFinding> findings);

    List<ImpactFinding> findByRunId(String impactAnalysisRunId);
}
