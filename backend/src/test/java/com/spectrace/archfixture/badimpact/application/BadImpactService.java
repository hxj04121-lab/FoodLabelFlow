package com.spectrace.archfixture.badimpact.application;

import com.spectrace.archfixture.badworkflow.infrastructure.WorkflowAdapter;

/** Intentionally violates the application-to-foreign-infrastructure boundary for an ArchUnit test. */
public class BadImpactService {
    private final WorkflowAdapter adapter;

    public BadImpactService(WorkflowAdapter adapter) {
        this.adapter = adapter;
    }

    public WorkflowAdapter adapter() {
        return adapter;
    }
}
