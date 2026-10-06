package com.spectrace.impact.application;

import com.spectrace.catalog.application.port.SpecificationVersionLookupPort;
import com.spectrace.catalog.application.port.SpecificationVersionLookupPort.SpecificationVersionFacts;
import com.spectrace.impact.application.ImpactAnalysisView.FindingView;
import com.spectrace.impact.application.port.ChangeRequestRepository;
import com.spectrace.impact.application.port.ImpactAnalysisRunRepository;
import com.spectrace.impact.application.port.ImpactFindingRepository;
import com.spectrace.impact.application.port.ImpactIntegration;
import com.spectrace.impact.application.port.ImpactIntegration.Permission;
import com.spectrace.impact.application.port.ReviewTaskLinkageRepository;
import com.spectrace.impact.application.strategy.ImpactStrategy;
import com.spectrace.impact.application.strategy.ImpactStrategyRegistry;
import com.spectrace.impact.application.strategy.ProductImpactAssessment;
import com.spectrace.impact.domain.ChangeRequest;
import com.spectrace.impact.domain.ChangeRequestStatus;
import com.spectrace.impact.domain.ChangeType;
import com.spectrace.impact.domain.ImpactAnalysisRun;
import com.spectrace.impact.domain.ImpactFinding;
import com.spectrace.impact.domain.ImpactRunStatus;
import com.spectrace.impact.domain.ReviewTaskLinkage;
import com.spectrace.impact.domain.ReviewTaskStatus;
import com.spectrace.validation.application.port.RuleSetVersionRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import static com.spectrace.shared.contract.ContractValues.requiredText;

/**
 * Runs and reads change impact analyses (SCRUM-79). One run per change request: every
 * precondition and every classification is computed before the first write, then the run,
 * its findings, the ReviewTasks of REVIEW_REQUIRED findings, the audit event and the
 * SUBMITTED → ANALYZED transition commit together or not at all.
 */
public class ChangeImpactAnalysisService {

    private final ChangeRequestRepository changeRequests;
    private final SpecificationVersionLookupPort specifications;
    private final RuleSetVersionRepository ruleSets;
    private final RelevantProductDiscovery discovery;
    private final ImpactStrategyRegistry strategies;
    private final ImpactAnalysisApplicationService persistence;
    private final ImpactAnalysisRunRepository runs;
    private final ImpactFindingRepository findings;
    private final ReviewTaskLinkageRepository reviewTasks;
    private final ImpactIntegration integration;
    private final Clock clock;
    private final Supplier<String> ids;

    public ChangeImpactAnalysisService(
            ChangeRequestRepository changeRequests,
            SpecificationVersionLookupPort specifications,
            RuleSetVersionRepository ruleSets,
            RelevantProductDiscovery discovery,
            ImpactStrategyRegistry strategies,
            ImpactAnalysisApplicationService persistence,
            ImpactAnalysisRunRepository runs,
            ImpactFindingRepository findings,
            ReviewTaskLinkageRepository reviewTasks,
            ImpactIntegration integration,
            Clock clock,
            Supplier<String> ids
    ) {
        this.changeRequests = Objects.requireNonNull(changeRequests, "changeRequests");
        this.specifications = Objects.requireNonNull(specifications, "specifications");
        this.ruleSets = Objects.requireNonNull(ruleSets, "ruleSets");
        this.discovery = Objects.requireNonNull(discovery, "discovery");
        this.strategies = Objects.requireNonNull(strategies, "strategies");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.findings = Objects.requireNonNull(findings, "findings");
        this.reviewTasks = Objects.requireNonNull(reviewTasks, "reviewTasks");
        this.integration = Objects.requireNonNull(integration, "integration");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    public static Supplier<String> randomIds() {
        return () -> UUID.randomUUID().toString();
    }

    @Transactional
    public ImpactAnalysisView run(String changeRequestId, String ruleSetVersionId) {
        String actorId = integration.requireActor(Permission.RUN_IMPACT);
        if (ruleSetVersionId == null || ruleSetVersionId.isBlank()) {
            throw ImpactFailure.invalid("ruleSetVersionId must be supplied");
        }
        // The row lock serialises concurrent runs of one request before anything is read.
        ChangeRequest change = changeRequests.lockById(requiredText(changeRequestId, "changeRequestId"))
                .filter(found -> found.changeType() == ChangeType.INGREDIENT_SPEC)
                .orElseThrow(() -> ImpactFailure.notFound("Change request " + changeRequestId + " was not found"));

        List<ImpactAnalysisRun> existing = runs.findByChangeRequestId(change.changeRequestId());
        if (!existing.isEmpty()) {
            return replay(existing.getFirst(), ruleSetVersionId);
        }
        if (change.status() == ChangeRequestStatus.CANCELLED || change.status() == ChangeRequestStatus.DRAFT) {
            throw ImpactFailure.conflict("A " + change.status() + " change request cannot be analysed");
        }
        if (ruleSets.findActiveById(ruleSetVersionId).isEmpty()) {
            throw ImpactFailure.precondition("RULE_SET_NOT_ACTIVE",
                    "Rule set version " + ruleSetVersionId + " is unknown or not active");
        }
        String materialId = specifications.findById(change.versionChange().toVersionId())
                .map(SpecificationVersionFacts::supplierMaterialId)
                .orElseThrow(() -> new IllegalStateException("Change request " + change.changeRequestId()
                        + " references a missing specification version"));

        // Every 422 (no published label, adoption pending) surfaces here, before any write.
        ImpactStrategy strategy = strategies.require(change.changeType());
        List<ProductImpactAssessment> assessments = discovery.discover(materialId).stream()
                .map(product -> strategy.assess(change, product, ruleSetVersionId))
                .toList();

        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        String provenance = change.dataProvenanceId();
        String runId = ids.get();
        var run = new ImpactAnalysisRun(runId, "IAR-" + runId, change.changeRequestId(), ruleSetVersionId,
                ImpactRunStatus.COMPLETED, now, now, actorId, provenance);
        List<ImpactFinding> newFindings = assessments.stream()
                .map(assessment -> assessment.toFinding(ids.get(), runId, provenance))
                .toList();
        List<ReviewTaskLinkage> newTasks = reviewTasksFor(newFindings, actorId, now, provenance);

        ImpactAnalysisRun persisted = persistence.execute(run, newFindings, newTasks);
        if (!persisted.impactAnalysisRunId().equals(runId)) {
            // A concurrent run committed first; the persistence service returned it unchanged.
            return replay(persisted, ruleSetVersionId);
        }
        changeRequests.updateStatus(change.changeRequestId(), ChangeRequestStatus.SUBMITTED, ChangeRequestStatus.ANALYZED);
        return new ImpactAnalysisView(run, views(newFindings, newTasks), true);
    }

    /** Reads need an active identity only, like change-request reads; M4 owns any extra permission. */
    @Transactional(readOnly = true)
    public ImpactAnalysisView get(String impactAnalysisId) {
        integration.authenticate();
        ImpactAnalysisRun run = runs.findById(requiredText(impactAnalysisId, "impactAnalysisId"))
                .orElseThrow(() -> ImpactFailure.notFound("Impact analysis " + impactAnalysisId + " was not found"));
        return load(run);
    }

    private ImpactAnalysisView replay(ImpactAnalysisRun run, String ruleSetVersionId) {
        if (!run.ruleSetVersionId().equals(ruleSetVersionId)) {
            throw ImpactFailure.conflict("The change request already has an analysis under rule set "
                    + run.ruleSetVersionId());
        }
        return load(run);
    }

    private ImpactAnalysisView load(ImpactAnalysisRun run) {
        List<FindingView> views = findings.findByRunId(run.impactAnalysisRunId()).stream()
                .map(finding -> new FindingView(finding, finding.requiresReviewTask()
                        ? reviewTasks.findByFindingId(finding.impactFindingId())
                                .orElseThrow(() -> new IllegalStateException(
                                        "REVIEW_REQUIRED finding " + finding.impactFindingId() + " has no review task"))
                        : null))
                .toList();
        return new ImpactAnalysisView(run, views, false);
    }

    private List<ReviewTaskLinkage> reviewTasksFor(
            List<ImpactFinding> newFindings, String actorId, Instant now, String provenance) {
        List<ImpactFinding> reviewRequired = newFindings.stream().filter(ImpactFinding::requiresReviewTask).toList();
        if (reviewRequired.isEmpty()) {
            return List.of();
        }
        String assignee = integration.reviewTaskAssignee();
        var tasks = new ArrayList<ReviewTaskLinkage>();
        for (ImpactFinding finding : reviewRequired) {
            // The draft label reference stays null until M4 links the replacement draft.
            tasks.add(new ReviewTaskLinkage(ids.get(), finding.impactFindingId(), finding.productId(),
                    finding.currentLabelVersionId(), null, ReviewTaskStatus.OPEN, assignee, actorId, now, provenance));
        }
        return tasks;
    }

    private static List<FindingView> views(List<ImpactFinding> newFindings, List<ReviewTaskLinkage> tasks) {
        return newFindings.stream()
                .map(finding -> new FindingView(finding, tasks.stream()
                        .filter(task -> task.impactFindingId().equals(finding.impactFindingId()))
                        .findFirst().orElse(null)))
                .toList();
    }
}
