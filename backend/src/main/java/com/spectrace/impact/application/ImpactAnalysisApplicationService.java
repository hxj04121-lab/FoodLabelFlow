package com.spectrace.impact.application;

import com.spectrace.audit.application.port.ImpactAuditEventPort;
import com.spectrace.impact.application.port.ImpactAnalysisRunRepository;
import com.spectrace.impact.application.port.ImpactFindingRepository;
import com.spectrace.impact.application.port.ReviewTaskLinkageRepository;
import com.spectrace.impact.domain.ImpactAnalysisRun;
import com.spectrace.impact.domain.ImpactFinding;
import com.spectrace.impact.domain.ReviewTaskLinkage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Persists one pre-classified M1 impact result as a single atomic unit. */
@Service
public class ImpactAnalysisApplicationService {

    private final ImpactAnalysisRunRepository runs;
    private final ImpactFindingRepository findings;
    private final ReviewTaskLinkageRepository reviewTasks;
    private final ImpactAuditEventPort audit;

    public ImpactAnalysisApplicationService(
            ImpactAnalysisRunRepository runs,
            ImpactFindingRepository findings,
            ReviewTaskLinkageRepository reviewTasks,
            ImpactAuditEventPort audit
    ) {
        this.runs = Objects.requireNonNull(runs, "runs");
        this.findings = Objects.requireNonNull(findings, "findings");
        this.reviewTasks = Objects.requireNonNull(reviewTasks, "reviewTasks");
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    @Transactional
    public ImpactAnalysisRun execute(
            ImpactAnalysisRun run,
            List<ImpactFinding> impactFindings,
            List<ReviewTaskLinkage> reviewTaskLinks
    ) {
        Objects.requireNonNull(run, "run");
        List<ImpactFinding> persistedFindings = List.copyOf(
                Objects.requireNonNull(impactFindings, "impactFindings"));
        List<ReviewTaskLinkage> persistedLinks = List.copyOf(
                Objects.requireNonNull(reviewTaskLinks, "reviewTaskLinks"));
        validate(run, persistedFindings, persistedLinks);

        try {
            runs.save(run);
        } catch (ImpactRunAlreadyExistsException replay) {
            ImpactAnalysisRun existing = replay.existingRun();
            if (!existing.ruleSetVersionId().equals(run.ruleSetVersionId())) {
                throw ImpactFailure.conflict("The change request already has an analysis under a different rule set");
            }
            return existing;
        }
        findings.saveAll(run.impactAnalysisRunId(), persistedFindings);
        persistedLinks.forEach(reviewTasks::saveOrGetExisting);
        audit.recordImpactEvent(
                run.executedByUserId(),
                run.impactAnalysisRunId(),
                run.changeRequestId(),
                run.status().name(),
                run.dataProvenanceId());
        return run;
    }

    private static void validate(
            ImpactAnalysisRun run,
            List<ImpactFinding> findings,
            List<ReviewTaskLinkage> links
    ) {
        if (findings.stream().anyMatch(finding ->
                !run.impactAnalysisRunId().equals(finding.impactAnalysisRunId()))) {
            throw new IllegalArgumentException("All impact findings must belong to the supplied run");
        }

        Map<String, ImpactFinding> byId = findings.stream().collect(Collectors.toMap(
                ImpactFinding::impactFindingId, Function.identity()));
        Map<String, ReviewTaskLinkage> linksByFinding = links.stream().collect(Collectors.toMap(
                ReviewTaskLinkage::impactFindingId, Function.identity()));

        if (links.stream().anyMatch(link -> {
            ImpactFinding finding = byId.get(link.impactFindingId());
            return finding == null
                    || !finding.requiresReviewTask()
                    || !finding.productId().equals(link.productId())
                    || !finding.currentLabelVersionId().equals(link.currentLabelVersionId());
        })) {
            throw new IllegalArgumentException("Review task links must match REVIEW_REQUIRED findings");
        }
        if (byId.values().stream()
                .filter(ImpactFinding::requiresReviewTask)
                .anyMatch(finding -> !linksByFinding.containsKey(finding.impactFindingId()))) {
            throw new IllegalArgumentException("Every REVIEW_REQUIRED finding needs a review task link");
        }
    }
}
