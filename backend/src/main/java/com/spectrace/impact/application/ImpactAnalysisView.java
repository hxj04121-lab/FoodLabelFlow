package com.spectrace.impact.application;

import com.spectrace.impact.domain.ImpactAnalysisRun;
import com.spectrace.impact.domain.ImpactClassification;
import com.spectrace.impact.domain.ImpactFinding;
import com.spectrace.impact.domain.ReviewTaskLinkage;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A completed run with its findings ordered by productId. created is false for an
 * idempotent replay, so the web adapter can answer 200 instead of 201.
 */
public record ImpactAnalysisView(ImpactAnalysisRun run, List<FindingView> findings, boolean created) {

    public ImpactAnalysisView {
        Objects.requireNonNull(run, "run");
        findings = Objects.requireNonNull(findings, "findings").stream()
                .sorted(Comparator.comparing(view -> view.finding().productId()))
                .toList();
    }

    public long count(ImpactClassification classification) {
        return findings.stream().filter(view -> view.finding().classification() == classification).count();
    }

    /** reviewTask is present exactly for a REVIEW_REQUIRED finding. */
    public record FindingView(ImpactFinding finding, ReviewTaskLinkage reviewTask) {
        public FindingView {
            Objects.requireNonNull(finding, "finding");
            if (finding.requiresReviewTask() != (reviewTask != null)) {
                throw new IllegalStateException("Finding " + finding.impactFindingId()
                        + " must have a review task exactly when it is REVIEW_REQUIRED");
            }
            if (reviewTask != null && !reviewTask.impactFindingId().equals(finding.impactFindingId())) {
                throw new IllegalStateException("Review task " + reviewTask.reviewTaskId()
                        + " belongs to another finding");
            }
        }
    }
}
