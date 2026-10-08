package com.spectrace.impact.interfaces.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.spectrace.impact.application.ImpactAnalysisView;
import com.spectrace.impact.application.ImpactAnalysisView.FindingView;
import com.spectrace.impact.domain.ImpactClassification;
import com.spectrace.impact.domain.ImpactFinding;
import com.spectrace.impact.domain.ReviewTaskLinkage;

import java.util.List;

/** The S3 contract ImpactAnalysis resource; actor, run code and provenance stay internal. */
public record ImpactAnalysisResponse(
        String impactAnalysisId,
        String changeRequestId,
        String ruleSetVersionId,
        String status,
        String completedAt,
        long relevantProductCount,
        long noActionCount,
        long reviewRequiredCount,
        List<Finding> findings
) {
    public static ImpactAnalysisResponse from(ImpactAnalysisView view) {
        var run = view.run();
        return new ImpactAnalysisResponse(
                run.impactAnalysisRunId(),
                run.changeRequestId(),
                run.ruleSetVersionId(),
                run.status().name(),
                run.completedAt().toString(),
                view.findings().size(),
                view.count(ImpactClassification.NO_ACTION),
                view.count(ImpactClassification.REVIEW_REQUIRED),
                view.findings().stream().map(Finding::from).toList());
    }

    /** NoActionFinding or ReviewRequiredFinding, discriminated by outcome; reviewTask only on the latter. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Finding(
            String impactFindingId,
            String productId,
            ImpactClassification outcome,
            String currentFormulaVersionId,
            String proposedFormulaVersionId,
            String currentLabelVersionId,
            List<String> missingAllergenCodes,
            String explanation,
            ReviewTask reviewTask
    ) {
        static Finding from(FindingView view) {
            ImpactFinding finding = view.finding();
            return new Finding(
                    finding.impactFindingId(),
                    finding.productId(),
                    finding.classification(),
                    finding.currentFormulaVersionId(),
                    finding.proposedFormulaVersionId(),
                    finding.currentLabelVersionId(),
                    finding.missingAllergenCodes(),
                    finding.explanation(),
                    view.reviewTask() == null ? null : ReviewTask.from(view.reviewTask(), finding));
        }
    }

    /** ReviewTaskHandoff: draftLabelVersionId is always serialised, as null until M4 links a draft. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ReviewTask(
            String reviewTaskId,
            String impactFindingId,
            String productId,
            String currentFormulaVersionId,
            String currentLabelVersionId,
            String draftLabelVersionId
    ) {
        static ReviewTask from(ReviewTaskLinkage task, ImpactFinding finding) {
            return new ReviewTask(task.reviewTaskId(), task.impactFindingId(), task.productId(),
                    finding.currentFormulaVersionId(), task.currentLabelVersionId(), task.draftLabelVersionId());
        }
    }
}
