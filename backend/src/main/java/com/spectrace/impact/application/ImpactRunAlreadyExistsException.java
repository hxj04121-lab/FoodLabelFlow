package com.spectrace.impact.application;

import com.spectrace.impact.domain.ImpactAnalysisRun;

import java.util.Objects;

/** A business-key collision; callers can replay the existing run after checking its rule set. */
public class ImpactRunAlreadyExistsException extends RuntimeException {

    private final ImpactAnalysisRun existingRun;

    public ImpactRunAlreadyExistsException(ImpactAnalysisRun existingRun, RuntimeException cause) {
        super("An impact analysis already exists for change request " + existingRun.changeRequestId(), cause);
        this.existingRun = Objects.requireNonNull(existingRun, "existingRun");
    }

    public ImpactAnalysisRun existingRun() {
        return existingRun;
    }
}
