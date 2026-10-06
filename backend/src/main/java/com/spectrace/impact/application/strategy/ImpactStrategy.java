package com.spectrace.impact.application.strategy;

import com.spectrace.impact.application.RelevantProductTarget;
import com.spectrace.impact.domain.ChangeRequest;
import com.spectrace.impact.domain.ChangeType;

/**
 * Classifies one relevant product for one kind of change. Selected by changeType through
 * ImpactStrategyRegistry; implementations read through ports only and write nothing.
 */
public interface ImpactStrategy {

    ChangeType changeType();

    /**
     * Runs inside the caller's analysis transaction. A precondition that makes the whole
     * run invalid throws ImpactFailure; corrupt or inconsistent data throws an exception
     * that rolls the run back. Never returns a fallback classification.
     */
    ProductImpactAssessment assess(ChangeRequest change, RelevantProductTarget product, String ruleSetVersionId);
}
